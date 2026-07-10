package com.alibaba.dependencycheck.exception;

import com.alibaba.dependencycheck.model.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.stream.Collectors;


/**
 * 全局异常处理器
 * <p>
 * 统一处理所有 Controller 抛出的异常，返回标准格式的错误信息。
 * 避免将异常堆栈直接暴露给前端。
 * </p>
 *
 * <b>C2 核查修复（7/3）：</b>
 * <ul>
 *   <li>C2-01: handleIOException 脱敏 — 不再直接返回 e.getMessage()（可能暴露文件路径）</li>
 *   <li>C2-02: 新增 handleTypeMismatch — 统一处理路径参数类型不匹配（如 /api/projects/abc）</li>
 *   <li>C2-03: 新增 handleMissingParam — 统一处理缺少必填参数</li>
 *   <li>C2-04: 新增 handleMaxUploadSize — 统一处理超大文件上传</li>
 *   <li>C2-05: 新增 handleNoResourceFound — 统一处理 404</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * 处理 @Valid 参数校验异常（Spring Boot Validation）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, "参数校验失败: " + message);
    }

    // ==================== C2 核查新增处理器（7/3） ====================

    /**
     * C2-02: 处理路径参数类型不匹配异常
     * <p>
     * 例如 GET /api/projects/abc 时，abc 无法转为 Long，Spring 默认返回
     * 非标准格式的 400 错误。此处理器统一返回 Result 格式。
     * </p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = String.format("参数 '%s' 的值 '%s' 类型不正确，期望类型: %s",
                e.getName(), e.getValue(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知");
        log.warn("参数类型不匹配: {}", message);
        return Result.error(400, message);
    }

    /**
     * C2-03: 处理缺少必填请求参数异常
     * <p>
     * 例如 POST /api/tasks 缺少 projectId 参数时，统一返回 Result 格式。
     * </p>
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String message = "缺少必填参数: " + e.getParameterName();
        log.warn("缺少请求参数: {}", message);
        return Result.error(400, message);
    }

    /**
     * E2E-B2: 处理缺少上传文件异常
     * <p>
     * 当 POST /api/projects 缺少 file 参数（multipart form-data part）时触发。
     * 与 {@link #handleMissingParam} 不同，此处理器专门针对 multipart 请求中的文件部件缺失。
     * </p>
     */
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingFilePart(org.springframework.web.multipart.support.MissingServletRequestPartException e) {
        String message = "缺少必填文件: " + e.getRequestPartName();
        log.warn("缺少上传文件: {}", message);
        return Result.error(400, message);
    }

    /**
     * E2E-B2: 处理非 multipart 请求异常
     * <p>
     * 当上传接口收到非 multipart/form-data 请求时触发。
     * </p>
     */
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMultipartException(org.springframework.web.multipart.MultipartException e) {
        log.warn("文件上传请求格式错误: {}", e.getMessage());
        return Result.error(400, "文件上传请求格式错误，请使用 multipart/form-data 格式");
    }

    /**
     * C2-04: 处理文件上传大小超限异常
     * <p>
     * 当上传文件超过 multipart.max-file-size 配置时触发。
     * </p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("文件上传超过大小限制: {}", e.getMessage());
        return Result.error(413, "上传文件大小超过限制，最大支持 500MB");
    }

    /**
     * C2-05: 处理资源未找到异常（Spring Boot 3.2+）
     * <p>
     * 当访问不存在的静态资源或路径时触发，统一返回 404。
     * </p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("资源未找到: {}", e.getMessage());
        return Result.error(404, "请求的资源不存在");
    }

    // ==================== 文件与系统异常 ====================

    /**
     * 处理文件操作异常
     * <p>
     * C2-01 修复：脱敏处理 — 不直接返回 e.getMessage()（可能暴露服务器文件路径）。
     * 完整异常信息仅记录在日志中，前端返回通用错误提示。
     * </p>
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleIOException(IOException e) {
        log.error("文件操作异常", e);
        return Result.error(500, "文件操作失败，请稍后重试");
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统内部错误，请稍后重试");
    }
}
