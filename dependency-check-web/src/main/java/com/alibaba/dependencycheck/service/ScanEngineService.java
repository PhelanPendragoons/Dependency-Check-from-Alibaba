package com.alibaba.dependencycheck.service;

import lombok.extern.slf4j.Slf4j;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.utils.Settings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
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
 */
@Slf4j
@Service
public class ScanEngineService {

    @Value("${dependency-check.data-directory}")
    private String dataDirectory;

    @Value("${dependency-check.nvd-api-key:}")
    private String nvdApiKey;

    /**
     * 执行扫描并生成报告
     *
     * @param scanPath  待扫描的文件/目录路径
     * @param reportDir 报告输出目录
     * @return 扫描到的依赖列表
     */
    public List<org.owasp.dependencycheck.dependency.Dependency> scan(String scanPath, String reportDir) {
        Engine engine = null;
        try {
            // 1. 创建 Settings 对象并配置数据目录
            Settings settings = new Settings();
            settings.setString(Settings.KEYS.DATA_DIRECTORY, dataDirectory);

            // 配置 NVD API Key（如果设置了环境变量）
            if (nvdApiKey != null && !nvdApiKey.isEmpty()) {
                settings.setString(Settings.KEYS.NVD_API_KEY, nvdApiKey);
                log.info("已配置 NVD API Key");
            } else {
                log.warn("未配置 NVD API Key，NVD 数据更新可能较慢");
            }


            // 2. 创建 Engine 实例
            //    Engine(Settings) 构造函数会自动：
            //    - 加载配置
            //    - 发现所有分析器（通过 SPI 机制）
            //    - 打开 H2 数据库连接
            engine = new Engine(Engine.Mode.STANDALONE, settings);

            // 3. 执行扫描
            //    scan() 方法会递归扫描指定目录，根据文件类型匹配分析器
            //    只创建 Dependency 对象，不执行分析
            log.info("开始扫描: {}", scanPath);
            engine.scan(scanPath);

            // 4. 执行分析
            //    analyzeDependencies() 会按阶段依次执行所有分析器
            log.info("开始分析依赖...");
            engine.analyzeDependencies();

            // 5. 获取扫描结果（v9.0.0 返回 Dependency[] 数组）
            org.owasp.dependencycheck.dependency.Dependency[] depsArray = engine.getDependencies();
            List<org.owasp.dependencycheck.dependency.Dependency> dependencies = Arrays.asList(depsArray);
            log.info("扫描完成，发现 {} 个依赖", dependencies.size());

            // 6. 生成 HTML 报告（v9.0.0 使用 writeReports() 方法）
            File reportDirectory = new File(reportDir);
            if (!reportDirectory.exists()) {
                reportDirectory.mkdirs();
            }
            engine.writeReports("dependency-check", reportDirectory, "HTML");

            return dependencies;

        } catch (Exception e) {
            log.error("扫描失败", e);
            throw new RuntimeException("扫描失败: " + e.getMessage(), e);
        } finally {
            // 7. 释放资源
            //    必须调用 close() 方法，否则数据库连接不会释放
            if (engine != null) {
                engine.close();
            }
        }
    }
}
