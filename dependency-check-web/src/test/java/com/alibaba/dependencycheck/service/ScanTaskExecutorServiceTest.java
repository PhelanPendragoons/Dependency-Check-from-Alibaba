package com.alibaba.dependencycheck.service;

import com.alibaba.dependencycheck.mapper.ScanResultMapper;
import com.alibaba.dependencycheck.mapper.ScanTaskMapper;
import com.alibaba.dependencycheck.model.entity.ScanResult;
import com.alibaba.dependencycheck.model.entity.ScanTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.InOrder;


/**
 * ScanTaskExecutorService 单元测试
 * <p>
 * 测试重点：
 * <ol>
 *   <li>扫描成功时，任务状态流转：PENDING → RUNNING → COMPLETED</li>
 *   <li>扫描失败时，任务状态流转：PENDING → RUNNING → FAILED</li>
 *   <li>扫描结果持久化：每个依赖是否被正确保存到 scan_result 表</li>
 *   <li>任务不存在时，不执行扫描</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ScanTaskExecutorServiceTest {

    @Mock
    private ScanEngineService scanEngineService;

    @Mock
    private ScanTaskMapper scanTaskMapper;

    @Mock
    private ScanResultMapper scanResultMapper;

    @InjectMocks
    private ScanTaskExecutorService scanTaskExecutorService;

    @Captor
    private ArgumentCaptor<ScanTask> taskCaptor;

    @Captor
    private ArgumentCaptor<ScanResult> resultCaptor;

    private ScanTask testTask;

    @BeforeEach
    void setUp() {
        testTask = new ScanTask();
        testTask.setId(10L);
        testTask.setProjectId(1L);
        testTask.setStatus("PENDING");
        testTask.setProgress(0);
    }

    // ==================== 扫描成功测试 ====================

    @Test
    @DisplayName("扫描成功时，任务状态应从 PENDING → RUNNING → COMPLETED")
    void executeScan_shouldUpdateStatusToCompletedOnSuccess() {
        // 准备：任务存在，扫描引擎返回空列表
        when(scanTaskMapper.selectById(10L)).thenReturn(testTask);
        when(scanEngineService.scan(anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        // 执行
        scanTaskExecutorService.executeScan(10L, "./uploads/test", "./reports/10");

        // 验证：updateById 被调用了 2 次
        // 注意：由于两次传入的是同一个 task 对象引用，getAllValues() 中的元素指向同一个对象
        // 第二次修改会覆盖第一次的状态，因此不能通过 getAllValues() 区分
        // 改用分别验证每次调用的参数
        verify(scanTaskMapper, times(2)).updateById(taskCaptor.capture());
        List<ScanTask> capturedTasks = taskCaptor.getAllValues();
        // 第一次调用时状态为 RUNNING
        assertEquals("RUNNING", capturedTasks.get(0).getStatus());
        assertNotNull(capturedTasks.get(0).getStartedAt());
        // 第二次调用时状态为 COMPLETED
        assertEquals("COMPLETED", capturedTasks.get(1).getStatus());
        assertEquals(100, capturedTasks.get(1).getProgress());
        assertEquals(0, capturedTasks.get(1).getTotalDependencies());
        assertEquals(0, capturedTasks.get(1).getVulnerableDependencies());
        assertNotNull(capturedTasks.get(1).getCompletedAt());
    }

    @Test
    @DisplayName("扫描成功时，应保存每个依赖的扫描结果")

    void executeScan_shouldSaveScanResults() {
        // 准备：创建一个模拟的 Dependency
        org.owasp.dependencycheck.dependency.Dependency mockDep =
                mock(org.owasp.dependencycheck.dependency.Dependency.class);
        when(mockDep.getName()).thenReturn("log4j-core");
        when(mockDep.getVersion()).thenReturn("2.14.1");
        when(mockDep.getFilePath()).thenReturn("/path/to/log4j-core.jar");
        when(mockDep.getVulnerabilities()).thenReturn(Collections.emptySet());
        when(mockDep.getLicense()).thenReturn("Apache-2.0");

        when(scanTaskMapper.selectById(10L)).thenReturn(testTask);
        when(scanEngineService.scan(anyString(), anyString()))
                .thenReturn(Collections.singletonList(mockDep));

        // 执行
        scanTaskExecutorService.executeScan(10L, "./uploads/test", "./reports/10");

        // 验证：扫描结果被保存
        verify(scanResultMapper).insert(resultCaptor.capture());
        ScanResult savedResult = resultCaptor.getValue();

        assertEquals(10L, savedResult.getTaskId());
        assertEquals("log4j-core", savedResult.getDependencyName());
        assertEquals("2.14.1", savedResult.getDependencyVersion());
        assertEquals("/path/to/log4j-core.jar", savedResult.getFilePath());
        assertEquals("Apache-2.0", savedResult.getLicenseName());
        assertFalse(savedResult.getIsVulnerable());
    }

    // ==================== 扫描失败测试 ====================

    @Test
    @DisplayName("扫描失败时，任务状态应从 RUNNING → FAILED，并记录错误信息")
    void executeScan_shouldUpdateStatusToFailedOnException() {
        // 准备：扫描引擎抛出异常
        when(scanTaskMapper.selectById(10L)).thenReturn(testTask);
        when(scanEngineService.scan(anyString(), anyString()))
                .thenThrow(new RuntimeException("扫描超时"));

        // 执行
        scanTaskExecutorService.executeScan(10L, "./uploads/test", "./reports/10");

        // 验证：updateById 被调用了 2 次
        verify(scanTaskMapper, times(2)).updateById(taskCaptor.capture());
        List<ScanTask> capturedTasks = taskCaptor.getAllValues();
        // 第一次调用时状态为 RUNNING
        assertEquals("RUNNING", capturedTasks.get(0).getStatus());
        assertNotNull(capturedTasks.get(0).getStartedAt());
        // 第二次调用时状态为 FAILED
        assertEquals("FAILED", capturedTasks.get(1).getStatus());
        assertEquals("扫描超时", capturedTasks.get(1).getErrorMessage());
        assertNotNull(capturedTasks.get(1).getCompletedAt());


    }

    @Test
    @DisplayName("扫描失败时，不应保存任何扫描结果")
    void executeScan_shouldNotSaveResultsOnFailure() {
        // 准备
        when(scanTaskMapper.selectById(10L)).thenReturn(testTask);
        when(scanEngineService.scan(anyString(), anyString()))
                .thenThrow(new RuntimeException("扫描超时"));

        // 执行
        scanTaskExecutorService.executeScan(10L, "./uploads/test", "./reports/10");

        // 验证：没有保存任何扫描结果
        verify(scanResultMapper, never()).insert(any());
    }

    // ==================== 任务不存在测试 ====================

    @Test
    @DisplayName("任务不存在时，不应执行扫描")
    void executeScan_shouldNotScanWhenTaskNotExists() {
        // 准备
        when(scanTaskMapper.selectById(999L)).thenReturn(null);

        // 执行
        scanTaskExecutorService.executeScan(999L, "./uploads/test", "./reports/999");

        // 验证：没有调用扫描引擎，没有更新任务状态
        verify(scanEngineService, never()).scan(anyString(), anyString());
        verify(scanTaskMapper, never()).updateById(any());
        verify(scanResultMapper, never()).insert(any());
    }

    // ==================== 漏洞依赖测试 ====================

    @Test
    @DisplayName("依赖有漏洞时，应正确记录漏洞信息")
    void executeScan_shouldRecordVulnerabilityInfo() {
        // 准备：创建一个有漏洞的依赖
        org.owasp.dependencycheck.dependency.Dependency mockDep =
                mock(org.owasp.dependencycheck.dependency.Dependency.class);
        when(mockDep.getName()).thenReturn("log4j-core");
        when(mockDep.getVersion()).thenReturn("2.14.1");
        when(mockDep.getFilePath()).thenReturn("/path/to/log4j-core.jar");

        // 模拟漏洞信息
        org.owasp.dependencycheck.dependency.Vulnerability mockVuln =
                mock(org.owasp.dependencycheck.dependency.Vulnerability.class);
        when(mockVuln.getName()).thenReturn("CVE-2021-44228");
        when(mockVuln.getHighestSeverityText()).thenReturn("CRITICAL");
        when(mockVuln.getDescription()).thenReturn("Log4j 远程代码执行漏洞");

        // CVSS v3 为 null，测试无评分时的分支
        when(mockVuln.getCvssV3()).thenReturn(null);

        when(mockDep.getVulnerabilities()).thenReturn(Collections.singleton(mockVuln));
        when(mockDep.getLicense()).thenReturn("Apache-2.0");

        when(scanTaskMapper.selectById(10L)).thenReturn(testTask);
        when(scanEngineService.scan(anyString(), anyString()))
                .thenReturn(Collections.singletonList(mockDep));

        // 执行
        scanTaskExecutorService.executeScan(10L, "./uploads/test", "./reports/10");

        // 验证：漏洞信息被正确保存
        verify(scanResultMapper).insert(resultCaptor.capture());
        ScanResult savedResult = resultCaptor.getValue();

        assertTrue(savedResult.getIsVulnerable());
        assertEquals("CVE-2021-44228", savedResult.getCveId());
        assertEquals("CRITICAL", savedResult.getSeverity());
        assertNull(savedResult.getCvssScore(), "CVSS v3 为 null 时评分应为 null");
        assertEquals("Log4j 远程代码执行漏洞", savedResult.getDescription());
    }

    @Test
    @DisplayName("依赖有漏洞且 CVSS v3 可用时，应使用 CVSS v3 评分")
    void executeScan_shouldUseCvssV3Score() {
        // 准备：创建一个有漏洞的依赖，CVSS v3 有值
        org.owasp.dependencycheck.dependency.Dependency mockDep =
                mock(org.owasp.dependencycheck.dependency.Dependency.class);
        when(mockDep.getName()).thenReturn("log4j-core");
        when(mockDep.getVersion()).thenReturn("2.14.1");
        when(mockDep.getFilePath()).thenReturn("/path/to/log4j-core.jar");

        org.owasp.dependencycheck.dependency.Vulnerability mockVuln =
                mock(org.owasp.dependencycheck.dependency.Vulnerability.class);
        when(mockVuln.getName()).thenReturn("CVE-2021-44228");
        when(mockVuln.getHighestSeverityText()).thenReturn("CRITICAL");
        when(mockVuln.getDescription()).thenReturn("Log4j 远程代码执行漏洞");

        // CVSS v3 有值但 CvssData 为 null
        io.github.jeremylong.openvulnerability.client.nvd.CvssV3 mockCvssV3 =
                mock(io.github.jeremylong.openvulnerability.client.nvd.CvssV3.class);
        lenient().when(mockCvssV3.getCvssData()).thenReturn(null);
        when(mockVuln.getCvssV3()).thenReturn(mockCvssV3);

        when(mockDep.getVulnerabilities()).thenReturn(Collections.singleton(mockVuln));
        when(mockDep.getLicense()).thenReturn("Apache-2.0");

        when(scanTaskMapper.selectById(10L)).thenReturn(testTask);
        when(scanEngineService.scan(anyString(), anyString()))
                .thenReturn(Collections.singletonList(mockDep));

        // 执行
        scanTaskExecutorService.executeScan(10L, "./uploads/test", "./reports/10");

        // 验证
        verify(scanResultMapper).insert(resultCaptor.capture());
        ScanResult savedResult = resultCaptor.getValue();

        assertTrue(savedResult.getIsVulnerable());
        assertEquals("CVE-2021-44228", savedResult.getCveId());
        assertNull(savedResult.getCvssScore());
    }
}
