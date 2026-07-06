package com.alibaba.dependencycheck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 跨域配置
 * <p>
 * 允许前端 Vue 应用跨域访问后端 API。
 * C4 核查修复（7/3）：CORS origin 从硬编码改为从 application.yml 读取，
 * 支持开发/生产环境切换。
 * </p>
 *
 * <b>配置方式：</b>
 * <ul>
 *   <li>开发环境：{@code app.cors.allowed-origin=http://localhost:5173}</li>
 *   <li>生产环境：{@code app.cors.allowed-origin=https://your-domain.com}</li>
 * </ul>
 *
 * <b>重要说明：</b>
 * <ul>
 *   <li>使用 {@code addAllowedOrigin()} 而非 {@code addAllowedOriginPattern()}，
 *       因为这里指定的是精确来源地址，不需要通配符匹配</li>
 *   <li>{@code addAllowedOriginPattern} 用于通配符匹配（如 {@code *.example.com}），
 *       而 {@code addAllowedOrigin} 用于精确匹配</li>
 *   <li>明确指定允许的来源，而非使用 {@code "*"}，
 *       避免违反 CORS 规范中"credentials=true 时 origin 不能为通配符"的限制</li>
 *   <li>生产部署时需将 {@code app.cors.allowed-origin} 替换为实际的前端域名</li>
 * </ul>
 */
@Configuration
public class CorsConfig {

    /** C4 修复：CORS origin 从配置文件读取，支持环境切换 */
    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // C4 修复：从配置文件读取允许的来源，而非硬编码
        config.addAllowedOrigin(allowedOrigin);
        // 允许所有请求头
        config.addAllowedHeader("*");
        // 允许所有 HTTP 方法
        config.addAllowedMethod("*");
        // 允许携带凭证（Cookie、Authorization 头等）
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
