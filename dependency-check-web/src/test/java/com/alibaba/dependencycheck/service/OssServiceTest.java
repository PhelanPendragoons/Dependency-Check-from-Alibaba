package com.alibaba.dependencycheck.service;

import com.alibaba.dependencycheck.config.OssConfig;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OssService 单元测试
 * <p>
 * D4（7/6）：覆盖 OSS 服务的全部 CRUD 操作和降级路径。
 * </p>
 *
 * <b>测试场景：</b>
 * <ol>
 *   <li>OSS 可用时：upload / download / delete / exists 正常路径</li>
 *   <li>OSS 不可用时：所有方法静默返回 false/null（降级）</li>
 *   <li>OSS 异常时：OSSException / 网络异常 → 返回 false/null 不抛异常</li>
 *   <li>便捷方法：buildReportKey / getObjectUrl 正确性</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OssServiceTest {

    @Mock
    private OSS ossClient;

    @Mock
    private OssConfig ossConfig;

    @InjectMocks
    private OssService ossService;

    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_ENDPOINT = "oss-cn-hangzhou.aliyuncs.com";
    private static final String TEST_KEY = "reports/1/dependency-check-report.html";

    @BeforeEach
    void setUp() {
        when(ossConfig.getBucketName()).thenReturn(TEST_BUCKET);
        when(ossConfig.getEndpoint()).thenReturn(TEST_ENDPOINT);
    }

    // ==================== isAvailable 测试 ====================

    @Test
    @DisplayName("OSS Client 和 Config 均非空时，isAvailable 应返回 true")
    void isAvailable_shouldReturnTrueWhenBothPresent() {
        assertTrue(ossService.isAvailable());
    }

    @Test
    @DisplayName("OSS Client 为 null 时，isAvailable 应返回 false")
    void isAvailable_shouldReturnFalseWhenClientNull() {
        OssService serviceWithoutOss = new OssService();
        assertFalse(serviceWithoutOss.isAvailable());
    }

    // ==================== upload 测试 ====================

    @Test
    @DisplayName("OSS 可用时，上传应成功并返回 true")
    void upload_shouldSucceedWhenOssAvailable() {
        byte[] content = "test report content".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(content);

        boolean result = ossService.upload(inputStream, TEST_KEY);

        assertTrue(result);
        verify(ossClient).putObject(eq(TEST_BUCKET), eq(TEST_KEY), any(InputStream.class));
    }

    @Test
    @DisplayName("OSS 不可用时，上传应返回 false 且不调用 OSS Client")
    void upload_shouldReturnFalseWhenOssUnavailable() {
        OssService serviceWithoutOss = new OssService();
        InputStream inputStream = new ByteArrayInputStream("test".getBytes());

        boolean result = serviceWithoutOss.upload(inputStream, TEST_KEY);

        assertFalse(result);
    }

    @Test
    @DisplayName("OSS 上传抛出 OSSException 时，应返回 false 而非抛出异常")
    void upload_shouldReturnFalseOnOssException() {
        OSSException mockOssEx = mock(OSSException.class);
        doThrow(mockOssEx)
                .when(ossClient).putObject(anyString(), anyString(), any(InputStream.class));
        InputStream inputStream = new ByteArrayInputStream("test".getBytes());

        boolean result = ossService.upload(inputStream, TEST_KEY);

        assertFalse(result);
    }

    @Test
    @DisplayName("OSS 上传抛出 RuntimeException 时，应返回 false 而非抛出异常")
    void upload_shouldReturnFalseOnRuntimeException() {
        doThrow(new RuntimeException("Network error"))
                .when(ossClient).putObject(anyString(), anyString(), any(InputStream.class));
        InputStream inputStream = new ByteArrayInputStream("test".getBytes());

        boolean result = ossService.upload(inputStream, TEST_KEY);

        assertFalse(result);
    }

    // ==================== download 测试 ====================

    @Test
    @DisplayName("OSS 可用且文件存在时，下载应返回 InputStream")
    void download_shouldReturnStreamWhenFileExists() {
        OSSObject mockOssObject = mock(OSSObject.class);
        ObjectMetadata mockMetadata = mock(ObjectMetadata.class);
        when(mockMetadata.getContentLength()).thenReturn(100L);
        when(mockOssObject.getObjectMetadata()).thenReturn(mockMetadata);
        InputStream expectedStream = new ByteArrayInputStream("content".getBytes());
        when(mockOssObject.getObjectContent()).thenReturn(expectedStream);

        when(ossClient.doesObjectExist(TEST_BUCKET, TEST_KEY)).thenReturn(true);
        when(ossClient.getObject(TEST_BUCKET, TEST_KEY)).thenReturn(mockOssObject);

        InputStream result = ossService.download(TEST_KEY);

        assertNotNull(result);
        verify(ossClient).getObject(TEST_BUCKET, TEST_KEY);
    }

    @Test
    @DisplayName("OSS 可用但文件不存在时，下载应返回 null")
    void download_shouldReturnNullWhenFileNotExists() {
        when(ossClient.doesObjectExist(TEST_BUCKET, TEST_KEY)).thenReturn(false);

        InputStream result = ossService.download(TEST_KEY);

        assertNull(result);
        verify(ossClient, never()).getObject(anyString(), anyString());
    }

    @Test
    @DisplayName("OSS 不可用时，下载应返回 null")
    void download_shouldReturnNullWhenOssUnavailable() {
        OssService serviceWithoutOss = new OssService();

        InputStream result = serviceWithoutOss.download(TEST_KEY);

        assertNull(result);
    }

    @Test
    @DisplayName("OSS 下载抛出异常时，应返回 null 而非抛出异常")
    void download_shouldReturnNullOnException() {
        when(ossClient.doesObjectExist(TEST_BUCKET, TEST_KEY)).thenThrow(new RuntimeException("Network error"));

        InputStream result = ossService.download(TEST_KEY);

        assertNull(result);
    }

    // ==================== delete 测试 ====================

    @Test
    @DisplayName("OSS 可用时，删除应成功并返回 true")
    void delete_shouldSucceedWhenOssAvailable() {
        boolean result = ossService.delete(TEST_KEY);

        assertTrue(result);
        verify(ossClient).deleteObject(TEST_BUCKET, TEST_KEY);
    }

    @Test
    @DisplayName("OSS 不可用时，删除应返回 false")
    void delete_shouldReturnFalseWhenOssUnavailable() {
        OssService serviceWithoutOss = new OssService();

        boolean result = serviceWithoutOss.delete(TEST_KEY);

        assertFalse(result);
    }

    @Test
    @DisplayName("OSS 删除抛出异常时，应返回 false")
    void delete_shouldReturnFalseOnException() {
        doThrow(new RuntimeException("Network error"))
                .when(ossClient).deleteObject(anyString(), anyString());

        boolean result = ossService.delete(TEST_KEY);

        assertFalse(result);
    }

    // ==================== exists 测试 ====================

    @Test
    @DisplayName("OSS 可用时，exists 应返回 OSS Client 的查询结果")
    void exists_shouldReturnOssClientResult() {
        when(ossClient.doesObjectExist(TEST_BUCKET, TEST_KEY)).thenReturn(true);

        assertTrue(ossService.exists(TEST_KEY));
        verify(ossClient).doesObjectExist(TEST_BUCKET, TEST_KEY);
    }

    @Test
    @DisplayName("OSS 文件不存在时，exists 应返回 false")
    void exists_shouldReturnFalseWhenFileNotExist() {
        when(ossClient.doesObjectExist(TEST_BUCKET, TEST_KEY)).thenReturn(false);

        assertFalse(ossService.exists(TEST_KEY));
    }

    @Test
    @DisplayName("OSS 不可用时，exists 应返回 false")
    void exists_shouldReturnFalseWhenOssUnavailable() {
        OssService serviceWithoutOss = new OssService();

        assertFalse(serviceWithoutOss.exists(TEST_KEY));
    }

    @Test
    @DisplayName("OSS exists 抛出异常时，应返回 false")
    void exists_shouldReturnFalseOnException() {
        when(ossClient.doesObjectExist(anyString(), anyString()))
                .thenThrow(new RuntimeException("Network error"));

        assertFalse(ossService.exists(TEST_KEY));
    }

    // ==================== 便捷方法测试 ====================

    @Test
    @DisplayName("buildReportKey 应生成正确的 OSS Object Key")
    void buildReportKey_shouldGenerateCorrectKey() {
        String key = ossService.buildReportKey(10L, "dependency-check-report.html");

        assertEquals("reports/10/dependency-check-report.html", key);
    }

    @Test
    @DisplayName("buildReportKey 应正确处理 Excel 文件名")
    void buildReportKey_shouldHandleExcelFileName() {
        String key = ossService.buildReportKey(5L, "dependency-check-report-5.xlsx");

        assertEquals("reports/5/dependency-check-report-5.xlsx", key);
    }

    @Test
    @DisplayName("getObjectUrl 应生成正确的 HTTPS URL")
    void getObjectUrl_shouldGenerateCorrectUrl() {
        String url = ossService.getObjectUrl(TEST_KEY);

        assertEquals("https://test-bucket.oss-cn-hangzhou.aliyuncs.com/reports/1/dependency-check-report.html", url);
    }

    @Test
    @DisplayName("OSS 不可用时，getObjectUrl 应返回 null")
    void getObjectUrl_shouldReturnNullWhenOssUnavailable() {
        OssService serviceWithoutOss = new OssService();

        assertNull(serviceWithoutOss.getObjectUrl(TEST_KEY));
    }
}
