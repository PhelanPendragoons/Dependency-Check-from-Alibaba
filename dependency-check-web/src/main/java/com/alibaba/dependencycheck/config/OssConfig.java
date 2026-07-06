package com.alibaba.dependencycheck.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置类
 * <p>
 * D4（7/6）：提供 OSS Client 单例 Bean，仅在 {@code app.oss.enabled=true} 时激活。
 * 支持通过环境变量注入凭证（OSS_ENDPOINT / OSS_ACCESS_KEY_ID /
 * OSS_ACCESS_KEY_SECRET / OSS_BUCKET_NAME），避免敏感信息写入配置文件。
 * </p>
 *
 * <b>使用方式：</b>
 * <ul>
 *   <li>开发环境 — 默认关闭（{@code enabled: false}），不创建 Bean</li>
 *   <li>生产环境 — 设置环境变量后将 {@code enabled} 改为 {@code true}</li>
 *   <li>调用方通过 {@code @Autowired(required = false)} 注入 {@link OssService}，
 *       当 OSS 未启用时该字段为 {@code null}，自动走本地降级</li>
 * </ul>
 *
 * <b>Bean 生命周期：</b>
 * <ul>
 *   <li>{@code @Bean} — 应用启动时创建 OSS Client 实例</li>
 *   <li>{@code @PreDestroy} — 应用关闭时调用 {@code ossClient.shutdown()} 释放连接</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.oss", name = "enabled", havingValue = "true")
public class OssConfig {

    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String bucketName;
    private OSS ossClient;

    /**
     * 从配置文件读取 OSS 凭证，支持环境变量占位符。
     * <p>
     * 使用构造函数注入而非 {@code @Value} 字段注入，便于单元测试。
     * Spring Boot 会自动解析 {@code ${OSS_ENDPOINT:}} 等占位符。
     * </p>
     */
    public OssConfig(
            @org.springframework.beans.factory.annotation.Value("${app.oss.endpoint}") String endpoint,
            @org.springframework.beans.factory.annotation.Value("${app.oss.access-key-id}") String accessKeyId,
            @org.springframework.beans.factory.annotation.Value("${app.oss.access-key-secret}") String accessKeySecret,
            @org.springframework.beans.factory.annotation.Value("${app.oss.bucket-name}") String bucketName) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.bucketName = bucketName;
    }

    /**
     * 创建 OSS Client Bean
     * <p>
     * 仅在 {@code app.oss.enabled=true} 且所有必要配置项非空时创建。
     * 如果缺少必要配置，记录警告日志并跳过 Bean 创建。
     * </p>
     *
     * @return OSS Client 实例，如果配置不完整则返回 {@code null}
     */
    @Bean
    public OSS ossClient() {
        if (endpoint == null || endpoint.isBlank()
                || accessKeyId == null || accessKeyId.isBlank()
                || accessKeySecret == null || accessKeySecret.isBlank()
                || bucketName == null || bucketName.isBlank()) {
            log.warn("OSS 已启用但配置不完整，跳过 OSS Client 创建。"
                    + "请检查 OSS_ENDPOINT / OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET / OSS_BUCKET_NAME 环境变量");
            return null;
        }

        try {
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            log.info("OSS Client 初始化成功: endpoint={}, bucket={}", endpoint, bucketName);
            return ossClient;
        } catch (Exception e) {
            log.error("OSS Client 初始化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取 Bucket 名称
     *
     * @return OSS Bucket 名称
     */
    public String getBucketName() {
        return bucketName;
    }

    /**
     * 获取 OSS Endpoint
     *
     * @return OSS Endpoint 地址
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 应用关闭时释放 OSS Client 资源
     */
    @jakarta.annotation.PreDestroy
    public void destroy() {
        if (ossClient != null) {
            try {
                ossClient.shutdown();
                log.info("OSS Client 已关闭");
            } catch (Exception e) {
                log.warn("OSS Client 关闭时异常: {}", e.getMessage());
            }
        }
    }
}
