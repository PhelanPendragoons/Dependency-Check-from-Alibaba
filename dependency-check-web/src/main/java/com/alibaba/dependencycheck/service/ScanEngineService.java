package com.alibaba.dependencycheck.service;

import com.alibaba.dependencycheck.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.utils.Settings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * DependencyCheck 引擎封装服务
 * <p>
 * 核心服务类，负责封装 OWASP DependencyCheck v9.0.0 的 Engine API。
 * 提供扫描、分析、报告生成等核心功能。
 * </p>
 *
 * <b>v9.0.0 API 说明：</b>
 * <ul>
 *   <li>Engine(Settings) — 构造函数，需要传入 Settings 对象</li>
 *   <li>engine.scan(String) — 扫描指定路径</li>
 *   <li>engine.analyzeDependencies() — 执行依赖分析</li>
 *   <li>engine.getDependencies() — 获取扫描结果（返回 Dependency[] 数组）</li>
 *   <li>engine.writeReports(applicationName, outputDir, format) — 生成报告</li>
 * </ul>
 *
 * <b>v9.0.0 线程/超时配置说明（核查点 B1-03, B1-09）：</b>
 * <ul>
 *   <li>分析线程数由 Engine 内部 {@code Runtime.availableProcessors()} 硬编码，无法通过 Settings 配置</li>
 *   <li>{@code MAX_DOWNLOAD_THREAD_POOL_SIZE} — 控制 NVD 数据下载线程数</li>
 *   <li>{@code CONNECTION_TIMEOUT} — HTTP 连接超时（默认 10000ms）</li>
 *   <li>{@code CONNECTION_READ_TIMEOUT} — HTTP 读取超时（默认 60000ms）</li>
 *   <li>{@code ANALYSIS_TIMEOUT} — 单个分析器超时</li>
 * </ul>
 */
@Slf4j
@Service
public class ScanEngineService {

    @Value("${dependency-check.data-directory}")
    private String dataDirectory;

    @Value("${dependency-check.nvd-api-key:}")
    private String nvdApiKey;

    // NVD Datafeed 镜像地址（7/16）：NVD API 直连被 Cloudflare 拦截（Java 客户端 520），
    // 配置后引擎改从 datafeed（本地/镜像 HTTP 源）下载 NVD 数据，绕开 API 直连
    // 格式示例：http://localhost:8888/nvdcve-2.0-{0}.json.gz（{0} 为年份占位符）
    @Value("${dependency-check.nvd-datafeed-url:}")
    private String nvdDatafeedUrl;

    // NVD 自动更新开关（7/16）：缓存预热完成后可关闭（演示/离线场景），
    // 避免每次扫描触发联网检查（默认 4 小时检查一次），网络不可用时导致扫描失败
    @Value("${dependency-check.nvd-auto-update:true}")
    private boolean nvdAutoUpdate;

    // OSS Index 分析器开关（7/16）：Sonatype OSS Index 为在线服务，未配置账号时
    // 大量依赖查询会触发 401 "Invalid credentials"，导致整个分析失败，默认关闭
    @Value("${dependency-check.ossindex-enabled:false}")
    private boolean ossIndexEnabled;

    // B1-03 修复：读取扫描线程数配置，映射到 NVD 下载线程池
    // 注意：v9.0.0 没有 MAX_SCAN_THREADS 键，分析线程由 Runtime.availableProcessors() 控制
    // 此处将配置值用于 NVD 数据下载线程池，减少网络资源竞争
    @Value("${dependency-check.max-scan-threads:4}")
    private int maxScanThreads;

    // B1-13 修复（7/3）：注入 Settings 单例 Bean 作为配置模板
    // 由于 v9.0.0 的 Settings 类不支持拷贝构造函数，保留 @Value 字段注入方式，
    // ScanEngineConfig @Bean 作为集中配置文档，可供未来版本升级后使用
    // 当升级到 Settings 支持 clone() 的版本后，可直接切换为 settings.clone() 模式
    @SuppressWarnings("unused")
    private final Settings baseSettings;

    public ScanEngineService(Settings baseSettings) {
        this.baseSettings = baseSettings;
    }

    /**
     * 执行扫描并生成报告
     *
     * @param scanPath  待扫描的文件/目录路径
     * @param reportDir 报告输出目录
     * @return 扫描到的依赖列表
     * @throws BusinessException 扫描路径不存在、不可读、报告目录不可写等情况
     */
    public List<org.owasp.dependencycheck.dependency.Dependency> scan(String scanPath, String reportDir) {
        // B1-08 修复：前置校验 — 扫描路径存在性和可读性
        Path scanPathObj = Paths.get(scanPath).normalize();
        if (!Files.exists(scanPathObj)) {
            throw new BusinessException("扫描路径不存在: " + scanPathObj);
        }
        if (!Files.isReadable(scanPathObj)) {
            throw new BusinessException("扫描路径不可读: " + scanPathObj);
        }
        log.info("扫描路径校验通过: {}", scanPathObj.toAbsolutePath());

        // B1-10 修复：前置校验 — 报告目录可写性
        File reportDirectory = new File(reportDir);
        if (!reportDirectory.exists()) {
            if (!reportDirectory.mkdirs()) {
                throw new BusinessException("无法创建报告目录: " + reportDir);
            }
        }
        if (!reportDirectory.canWrite()) {
            throw new BusinessException("报告目录不可写: " + reportDirectory.getAbsolutePath());
        }

        Engine engine = null;
        try {
            // 1. 创建 Settings 对象并配置
            // B1-13 说明（7/3）：v9.0.0 的 Settings 不支持拷贝构造函数，
            // 无法直接复用 ScanEngineConfig 单例 Bean。保留手动创建方式，
            // ScanEngineConfig @Bean 作为集中配置文档，供未来版本升级后使用。
            Settings settings = new Settings();
            settings.setString(Settings.KEYS.DATA_DIRECTORY, dataDirectory);

            // 配置 NVD API Key（如果设置了环境变量）
            if (nvdApiKey != null && !nvdApiKey.isEmpty()) {
                settings.setString(Settings.KEYS.NVD_API_KEY, nvdApiKey);
                log.info("已配置 NVD API Key");
            } else {
                log.warn("未配置 NVD API Key，NVD 数据更新可能较慢");
            }

            // 配置 NVD Datafeed 镜像（如果设置）：优先于 API 直连，规避 Cloudflare 拦截
            if (nvdDatafeedUrl != null && !nvdDatafeedUrl.isEmpty()) {
                settings.setString(Settings.KEYS.NVD_API_DATAFEED_URL, nvdDatafeedUrl);
                log.info("已配置 NVD Datafeed 镜像: {}", nvdDatafeedUrl);
            }

            // 配置 NVD 自动更新开关：关闭后完全使用本地缓存，不发起任何 NVD 网络请求
            settings.setBoolean(Settings.KEYS.AUTO_UPDATE, nvdAutoUpdate);
            if (!nvdAutoUpdate) {
                log.info("NVD 自动更新已关闭，使用本地缓存数据");
            }

            // OSS Index 分析器：在线服务，无凭据时会导致分析失败，默认禁用
            settings.setBoolean(Settings.KEYS.ANALYZER_OSSINDEX_ENABLED, ossIndexEnabled);
            if (!ossIndexEnabled) {
                log.info("OSS Index 分析器已禁用（漏洞检测使用 NVD 本地数据）");
            }

            // .NET Assembly 分析器：依赖本机 dotnet 运行时，缺失时初始化异常会导致
            // 整个分析失败（7/16 实测）；本系统面向 Java 项目扫描，直接禁用
            settings.setBoolean(Settings.KEYS.ANALYZER_ASSEMBLY_ENABLED, false);

            // B1-03 修复：配置扫描线程数（映射到 NVD 下载线程池）
            settings.setString(Settings.KEYS.MAX_DOWNLOAD_THREAD_POOL_SIZE, String.valueOf(maxScanThreads));
            log.info("扫描线程数(下载池): {}", maxScanThreads);

            // B1-09 修复：设置网络超时参数
            settings.setString(Settings.KEYS.CONNECTION_TIMEOUT, "30000");
            settings.setString(Settings.KEYS.CONNECTION_READ_TIMEOUT, "600000");
            settings.setString(Settings.KEYS.ANALYSIS_TIMEOUT, "300000");
            log.debug("超时配置: 连接=30s, 读取=600s, 分析=300s");

            // 2. 创建 Engine 实例
            engine = new Engine(Engine.Mode.STANDALONE, settings);

            // 3. 执行扫描
            //    scan() 方法会递归扫描指定目录，根据文件类型匹配分析器
            //    只创建 Dependency 对象，不执行分析
            log.info("开始扫描: {}", scanPathObj.toAbsolutePath());
            engine.scan(scanPath);

            // 4. 执行分析
            //    analyzeDependencies() 会按阶段依次执行所有分析器
            log.info("开始分析依赖...");
            engine.analyzeDependencies();

            // 5. 获取扫描结果（v9.0.0 返回 Dependency[] 数组）
            org.owasp.dependencycheck.dependency.Dependency[] depsArray = engine.getDependencies();
            List<org.owasp.dependencycheck.dependency.Dependency> dependencies = Arrays.asList(depsArray);
            log.info("扫描完成，发现 {} 个依赖", dependencies.size());

            // 6. 生成 HTML 报告
            // B1-07 说明：v9.0.0 的 writeReports() 格式名大小写不敏感，
            // 引擎内部通过 ReportFormat.valueOfIgnoreCase() 处理
            engine.writeReports("dependency-check", reportDirectory, "HTML");
            log.info("HTML 报告已生成到: {}", reportDirectory.getAbsolutePath());

            return dependencies;

        } catch (BusinessException e) {
            // 业务异常直接抛出，不包装
            throw e;
        } catch (Exception e) {
            // B1-08 修复：统一异常处理，区分不同错误类型
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("扫描失败: {}", errorMsg, e);
            throw new BusinessException("扫描失败: " + errorMsg);
        } finally {
            // 7. 释放资源
            //    必须调用 close() 方法，否则数据库连接不会释放
            if (engine != null) {
                engine.close();
            }
        }
    }
}
