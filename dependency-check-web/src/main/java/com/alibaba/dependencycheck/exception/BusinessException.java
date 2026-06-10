package com.alibaba.dependencycheck.exception;

import lombok.Getter;

/**
 * 业务异常类
 * <p>
 * 用于抛出可预知的业务错误，例如：
 * - "项目不存在"
 * - "扫描任务正在运行"
 * - "文件格式不支持"
 * 全局异常处理器会捕获此异常并返回标准格式的错误信息。
 * </p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }
}
