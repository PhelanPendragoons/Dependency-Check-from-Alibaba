package com.alibaba.dependencycheck.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) 配置
 * <p>
 * SpringDoc 自动扫描所有 @RestController 并生成 API 文档。
 * 访问路径：
 * <ul>
 *   <li>Swagger UI：<a href="http://localhost:8080/swagger-ui.html">/swagger-ui.html</a></li>
 *   <li>OpenAPI JSON：<a href="http://localhost:8080/v3/api-docs">/v3/api-docs</a></li>
 * </ul>
 * </p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dependencyCheckOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("应用依赖安全与合规分析平台 API")
                        .description("""
                                基于 OWASP Dependency-Check 9.0.0 构建的轻量级依赖安全扫描平台。

                                ## 核心功能
                                - **项目管理**：上传 ZIP 项目包、查询、删除
                                - **扫描任务**：创建异步扫描、状态跟踪、进度查询、取消
                                - **报告下载**：HTML / Excel / PDF 三种格式
                                - **仪表盘**：聚合统计、漏洞等级分布

                                ## 接口规范
                                - 统一响应格式：`Result<T>`（code=200 成功，其他失败）
                                - 分页响应格式：`Result<PageResult<T>>`
                                - 异常自动处理：`GlobalExceptionHandler`
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PhelanPendragoons")
                                .email("pangzhihan163@163.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
