package com.alibaba.dependencycheck.config;

import org.owasp.dependencycheck.utils.Settings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DependencyCheck 引擎配置类
 * <p>
 * B1-13 优化（7/3）：将 {@link Settings} 对象定义为单例 Bean，
 * 避免每次扫描都重新创建 Settings 实例，减少初始化开销。
 * </p>
 *
 * <b>使用方式：</b>
 * <ul>
 *   <li>{@link com.alibaba.dependencycheck.service.ScanEngineService} 注入此 Bean</li>
 *   <li>每次 {@code scan()} 调用时通过 {@code new Settings(settings)} 拷贝构造函数
 *       创建线程安全的副本（或后续理想情况下切换为 {@code settings.clone()}）</li>
 *   <li>注意：Settings 对象本身非线程安全，必须在每次扫描时拷贝使用</li>
 * </ul>
 *
 * <b>性能收益：</b>
 * <ul>
 *   <li>Settings 的初始化涉及系统属性读取和默认值设置</li>
 *   <li>改为单例后，仅首次创建时有开销，后续扫描复用</li>
 *   <li>扫描频率越高，收益越明显</li>
 * </ul>
 */
@Configuration
public class ScanEngineConfig {

    @Value("${dependency-check.data-directory}")
    private String dataDirectory;

    @Value("${dependency-check.nvd-api-key:}")
    private String nvdApiKey;

    @Value("${dependency-check.max-scan-threads:4}")
    private int maxScanThreads;

    /**
     * B1-13: 提供 Settings 单例 Bean
     * <p>
     * 预配置好 DATA_DIRECTORY、NVD_API_KEY、MAX_DOWNLOAD_THREAD_POOL_SIZE、
     * 以及网络超时参数。每次扫描时通过 {@code new Settings(settings)} 拷贝使用。
     * </p>
     *
     * @return 预配置的 Settings 实例（单例，非线程安全，使用时需拷贝）
     */
    @Bean
    public Settings dependencyCheckSettings() {
        Settings settings = new Settings();
        settings.setString(Settings.KEYS.DATA_DIRECTORY, dataDirectory);

        // 配置 NVD API Key
        if (nvdApiKey != null && !nvdApiKey.isEmpty()) {
            settings.setString(Settings.KEYS.NVD_API_KEY, nvdApiKey);
        }

        // 配置 NVD 下载线程池大小
        settings.setString(Settings.KEYS.MAX_DOWNLOAD_THREAD_POOL_SIZE, String.valueOf(maxScanThreads));

        // 配置网络超时参数
        settings.setString(Settings.KEYS.CONNECTION_TIMEOUT, "30000");
        settings.setString(Settings.KEYS.CONNECTION_READ_TIMEOUT, "600000");
        settings.setString(Settings.KEYS.ANALYSIS_TIMEOUT, "300000");

        return settings;
    }
}
