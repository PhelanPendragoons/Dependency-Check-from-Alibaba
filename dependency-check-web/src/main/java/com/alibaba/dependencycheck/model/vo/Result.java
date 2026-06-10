package com.alibaba.dependencycheck.model.vo;

import lombok.Data;

/**
 * 统一 API 返回结果
 * <p>
 * 所有 Controller 接口统一使用此类封装返回结果，
 * 前端可以根据 code 判断请求是否成功。
 * </p>
 *
 * @param <T> 返回数据的类型
 */
@Data
public class Result<T> {

    /** 状态码（200=成功，其他=失败） */
    private int code;

    /** 提示信息 */
    private String message;

    /** 返回数据 */
    private T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功（无返回数据）
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /**
     * 成功（有返回数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 失败（自定义状态码和消息）
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 失败（默认状态码500）
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}
