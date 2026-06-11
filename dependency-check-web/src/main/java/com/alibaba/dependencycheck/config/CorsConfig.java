package com.alibaba.dependencycheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 跨域配置
 * <p>
 * 允许前端 Vue 应用（开发服务器 localhost:5173）跨域访问后端 API。
 * </p>
 *
 * <b>重要说明：</b>
 * <ul>
 *   <li>使用 {@code addAllowedOriginPattern()} 而非 {@code addAllowedOrigin("*")}，
 *       因为 {@code setAllowCredentials(true)} 时不允许使用通配符 origin</li>
 *   <li>明确指定允许的来源 {@code http://localhost:5173}，而非使用 {@code "*"}，
 *       避免违反 CORS 规范中"credentials=true 时 origin 不能为通配符"的限制</li>
 *   <li>生产部署时需将 {@code localhost:5173} 替换为实际的前端域名</li>
 * </ul>
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 明确指定允许的来源（开发环境为 Vue 开发服务器）
        // 注意：不能使用 addAllowedOriginPattern("*") 与 setAllowCredentials(true) 同时使用
        config.addAllowedOriginPattern("http://localhost:5173");
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
