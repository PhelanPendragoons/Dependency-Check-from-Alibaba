package com.alibaba.dependencycheck.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 阿里云 OSS 对象存储服务
 * <p>
 * D4（7/6）：封装 OSS 的 CRUD 操作（upload / download / delete / exists），
 * 为报告文件的远程存储提供统一接口。
 * </p>
 *
 * <b>设计原则：</b>
 * <ul>
 *   <li><b>静默降级</b> — OSS 未启用时（{@code ossClient == null}），所有方法静默返回
 *       {@code false/null}，不抛异常。调用方检查返回值即可判断是否需要本地降级。</li>
 *   <li><b>调用方管理流</b> — {@code download()} 返回的 {@link InputStream} 由调用方负责关闭。</li>
 *   <li><b>OSS 异常不传播</b> — 所有 OSS 异常在内部 catch，记录日志后返回失败标志，
 *       确保 OSS 故障不影响主业务流程。</li>
 * </ul>
 *
 * <b>Object Key 命名规范：</b>
 * <pre>
 *   reports/{taskId}/dependency-check-report.html
 *   reports/{taskId}/dependency-check-report-{taskId}.xlsx
 *   reports/{taskId}/dependency-check-report-{taskId}.pdf
 * </pre>
 */
@Slf4j
@Service
public class OssService {

    /**
     * OSS 客户端（可选注入）。
     * <p>
     * 当 {@code app.oss.enabled=false} 或 OSS 配置不完整时，
     * {@link com.alibaba.dependencycheck.config.OssConfig} 不创建 Bean，
     * 此字段为 {@code null}，所有方法自动退化为 no-op。
     * </p>
     */
    @Autowired(required = false)
    private OSS ossClient;

    /**
     * Bucket 名称（可选注入，与 OSS Client 同步存在/不存在）。
     */
    @Autowired(required = false)
    private com.alibaba.dependencycheck.config.OssConfig ossConfig;

    /**
     * OSS 服务是否可用
     *
     * @return true 如果 OSS Client 已初始化且配置完整
     */
    public boolean isAvailable() {
        return ossClient != null && ossConfig != null;
    }

    // ==================== 核心 CRUD ====================

    /**
     * 上传文件到 OSS
     *
     * @param inputStream 文件输入流（调用方负责关闭）
     * @param objectKey   OSS 对象 Key，如 {@code reports/1/dependency-check-report.html}
     * @return true 如果上传成功
     */
    public boolean upload(InputStream inputStream, String objectKey) {
        if (!isAvailable()) {
            log.debug("OSS 未启用，跳过上传: {}", objectKey);
            return false;
        }

        try {
            String bucketName = ossConfig.getBucketName();
            ossClient.putObject(bucketName, objectKey, inputStream);
            log.info("OSS 上传成功: bucket={}, key={}", bucketName, objectKey);
            return true;
        } catch (OSSException e) {
            log.warn("OSS 上传失败（服务端错误）: key={}, code={}, message={}",
                    objectKey, e.getErrorCode(), e.getErrorMessage());
            return false;
        } catch (Exception e) {
            log.warn("OSS 上传失败（客户端/网络错误）: key={}, message={}",
                    objectKey, e.getMessage());
            return false;
        }
    }

    /**
     * 从 OSS 下载文件
     *
     * @param objectKey OSS 对象 Key
     * @return 文件输入流，调用方负责关闭；如果 OSS 不可用或文件不存在则返回 {@code null}
     */
    public InputStream download(String objectKey) {
        if (!isAvailable()) {
            log.debug("OSS 未启用，跳过下载: {}", objectKey);
            return null;
        }

        try {
            String bucketName = ossConfig.getBucketName();
            if (!ossClient.doesObjectExist(bucketName, objectKey)) {
                log.debug("OSS 对象不存在: bucket={}, key={}", bucketName, objectKey);
                return null;
            }
            OSSObject ossObject = ossClient.getObject(bucketName, objectKey);
            log.info("OSS 下载成功: bucket={}, key={}, size={}",
                    bucketName, objectKey, ossObject.getObjectMetadata().getContentLength());
            return ossObject.getObjectContent();
        } catch (OSSException e) {
            log.warn("OSS 下载失败（服务端错误）: key={}, code={}, message={}",
                    objectKey, e.getErrorCode(), e.getErrorMessage());
            return null;
        } catch (Exception e) {
            log.warn("OSS 下载失败（客户端/网络错误）: key={}, message={}",
                    objectKey, e.getMessage());
            return null;
        }
    }

    /**
     * 删除 OSS 上的文件
     *
     * @param objectKey OSS 对象 Key
     * @return true 如果删除成功（或文件本就不存在）
     */
    public boolean delete(String objectKey) {
        if (!isAvailable()) {
            log.debug("OSS 未启用，跳过删除: {}", objectKey);
            return false;
        }

        try {
            String bucketName = ossConfig.getBucketName();
            ossClient.deleteObject(bucketName, objectKey);
            log.info("OSS 删除成功: bucket={}, key={}", bucketName, objectKey);
            return true;
        } catch (OSSException e) {
            log.warn("OSS 删除失败（服务端错误）: key={}, code={}, message={}",
                    objectKey, e.getErrorCode(), e.getErrorMessage());
            return false;
        } catch (Exception e) {
            log.warn("OSS 删除失败（客户端/网络错误）: key={}, message={}",
                    objectKey, e.getMessage());
            return false;
        }
    }

    /**
     * 检查 OSS 上是否存在指定文件
     *
     * @param objectKey OSS 对象 Key
     * @return true 如果文件存在
     */
    public boolean exists(String objectKey) {
        if (!isAvailable()) {
            return false;
        }

        try {
            String bucketName = ossConfig.getBucketName();
            boolean result = ossClient.doesObjectExist(bucketName, objectKey);
            log.debug("OSS exists 查询: bucket={}, key={}, result={}", bucketName, objectKey, result);
            return result;
        } catch (OSSException e) {
            log.warn("OSS exists 查询失败（服务端错误）: key={}, code={}, message={}",
                    objectKey, e.getErrorCode(), e.getErrorMessage());
            return false;
        } catch (Exception e) {
            log.warn("OSS exists 查询失败（客户端/网络错误）: key={}, message={}",
                    objectKey, e.getMessage());
            return false;
        }
    }

    // ==================== 报告专用便捷方法 ====================

    /**
     * 构建报告的 OSS Object Key
     *
     * @param taskId  扫描任务 ID
     * @param fileName 报告文件名（如 dependency-check-report.html）
     * @return OSS Object Key
     */
    public String buildReportKey(Long taskId, String fileName) {
        return "reports/" + taskId + "/" + fileName;
    }

    /**
     * 获取 OSS 对象的公开访问 URL
     *
     * @param objectKey OSS 对象 Key
     * @return 公开访问 URL，如果 OSS 不可用则返回 {@code null}
     */
    public String getObjectUrl(String objectKey) {
        if (!isAvailable()) {
            return null;
        }
        try {
            String bucketName = ossConfig.getBucketName();
            // https://{bucket}.{endpoint}/{key}
            return "https://" + bucketName + "." + ossConfig.getEndpoint() + "/" + objectKey;
        } catch (Exception e) {
            log.warn("获取 OSS URL 失败: key={}, message={}", objectKey, e.getMessage());
            return null;
        }
    }
}
