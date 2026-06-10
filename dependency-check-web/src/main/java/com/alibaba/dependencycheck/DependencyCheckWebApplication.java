package com.alibaba.dependencycheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用依赖安全与合规分析平台 - 启动类
 *
 * @author alibaba-dependency-check
 */
@SpringBootApplication
public class DependencyCheckWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(DependencyCheckWebApplication.class, args);
    }
}
