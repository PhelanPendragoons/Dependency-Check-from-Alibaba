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

    // B1-03 修复：读取扫描线程数配置，映射到 NVD 下载线程池
    // 注意：v9.0.0 没有 MAX_SCAN_THREADS 键，分析线程由 Runtime.availableProcessors() 控制
    // 此处将配置值用于 NVD 数据下载线程池，减少网络资源竞争
    @Value("${dependency-check.max-scan-threads:4}")
    private int maxScanThreads;

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
            Settings settings = new Settings();
            settings.setString(Settings.KEYS.DATA_DIRECTORY, dataDirectory);

            // 配置 NVD API Key（如果设置了环境变量）
            if (nvdApiKey != null && !nvdApiKey.isEmpty()) {
                settings.setString(Settings.KEYS.NVD_API_KEY, nvdApiKey);
                log.info("已配置 NVD API Key");
            } else {
                log.warn("未配置 NVD API Key，NVD 数据更新可能较慢");
            }

            // B1-03 修复：配置扫描线程数（映射到 NVD 下载线程池）
            // v9.0.0 分析线程由 Runtime.availableProcessors() 硬编码，无法通过 Settings 控制
            // 此处仅控制 NVD 数据下载的并发线程数
            settings.setString(Settings.KEYS.MAX_DOWNLOAD_THREAD_POOL_SIZE, String.valueOf(maxScanThreads));
            log.info("扫描线程数(下载池): {}", maxScanThreads);

            // B1-09 修复：设置网络超时参数，防止 NVD 首次下载时无限阻塞
            // CONNECTION_TIMEOUT: 建立连接超时（默认 10s，调整为 30s）
            settings.setString(Settings.KEYS.CONNECTION_TIMEOUT, "30000");
            // CONNECTION_READ_TIMEOUT: 数据读取超时（默认 60s，NVD 全量下载需更长时间）
            settings.setString(Settings.KEYS.CONNECTION_READ_TIMEOUT, "600000");
            // ANALYSIS_TIMEOUT: 单个分析器执行超时，防止某个分析器卡死
            settings.setString(Settings.KEYS.ANALYSIS_TIMEOUT, "300000");
            log.debug("超时配置: 连接=30s, 读取=600s, 分析=300s");

            // 2. 创建 Engine 实例
            //    Engine(Settings) 构造函数会自动：
            //    - 加载配置
            //    - 发现所有分析器（通过 SPI 机制）
            //    - 打开 H2 数据库连接
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
