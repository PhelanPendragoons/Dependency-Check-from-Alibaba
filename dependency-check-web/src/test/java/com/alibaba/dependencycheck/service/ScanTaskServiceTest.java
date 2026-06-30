package com.alibaba.dependencycheck.service;

import com.alibaba.dependencycheck.exception.BusinessException;
import com.alibaba.dependencycheck.mapper.ProjectMapper;
import com.alibaba.dependencycheck.mapper.ScanResultMapper;
import com.alibaba.dependencycheck.mapper.ScanTaskMapper;
import com.alibaba.dependencycheck.model.dto.ScanResultDTO;
import com.alibaba.dependencycheck.model.dto.ScanTaskDTO;
import com.alibaba.dependencycheck.model.entity.Project;
import com.alibaba.dependencycheck.model.entity.ScanResult;
import com.alibaba.dependencycheck.model.entity.ScanTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ScanTaskService 单元测试
 * <p>
 * 测试重点：
 * <ol>
 *   <li>创建扫描任务时状态是否正确初始化为 PENDING</li>
 *   <li>查询任务详情时是否返回正确的任务信息</li>
 *   <li>查询扫描结果时是否返回结果列表</li>
 *   <li>不存在的任务/项目是否抛出 BusinessException</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ScanTaskServiceTest {

    @Mock
    private ScanTaskExecutorService scanTaskExecutorService;

    @Mock
    private ScanTaskMapper scanTaskMapper;

    @Mock
    private ScanResultMapper scanResultMapper;

    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private ScanTaskService scanTaskService;

    @Captor
    private ArgumentCaptor<ScanTask> taskCaptor;

    private Project testProject;
    private ScanTask testTask;

    @BeforeEach
    void setUp() {
        testProject = new Project();
        testProject.setId(1L);
        testProject.setName("test-project");
        testProject.setFilePath("./uploads/test-project");

        testTask = new ScanTask();
        testTask.setId(10L);
        testTask.setProjectId(1L);
        testTask.setStatus("PENDING");
        testTask.setProgress(0);

        // 设置 reportDir
        ReflectionTestUtils.setField(scanTaskService, "reportDir", "./reports");
    }

    // ==================== 创建任务测试 ====================

    @Test
    @DisplayName("创建扫描任务时，状态应初始化为 PENDING，进度为 0")
    void createTask_shouldInitializeStatusAsPending() {
        // 准备
        when(projectMapper.selectById(1L)).thenReturn(testProject);
        // B3-07: 模拟无活动任务（selectList 返回空列表）
        when(scanTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        // 使用 doAnswer 模拟 MyBatis-Plus 的 insert 方法设置 ID
        doAnswer(invocation -> {
            ScanTask task = invocation.getArgument(0);
            task.setId(10L); // 模拟数据库自增 ID
            return 1;
        }).when(scanTaskMapper).insert(any(ScanTask.class));

        // 执行
        ScanTaskDTO result = scanTaskService.createTask(1L);

        // 验证
        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        assertEquals(0, result.getProgress());

        // 验证异步扫描被触发（使用正确的参数）
        verify(scanTaskExecutorService).executeScan(eq(10L), eq("./uploads/test-project"), eq("./reports/10"));
    }

    @Test
    @DisplayName("为不存在的项目创建扫描任务时，应抛出 BusinessException")
    void createTask_shouldThrowExceptionWhenProjectNotExists() {
        // 准备
        when(projectMapper.selectById(999L)).thenReturn(null);

        // 执行 & 验证
        assertThrows(BusinessException.class, () -> scanTaskService.createTask(999L));
        verify(scanTaskMapper, never()).insert(any());
        verify(scanTaskExecutorService, never()).executeScan(anyLong(), anyString(), anyString());
    }

    // ==================== B3-07 并发任务控制测试 ====================

    @Test
    @DisplayName("同一项目已有 RUNNING/PENDING 任务时，应拒绝创建新任务")
    void createTask_shouldRejectWhenActiveTaskExists() {
        // 准备：项目存在
        when(projectMapper.selectById(1L)).thenReturn(testProject);

        // 已有 RUNNING 任务
        ScanTask activeTask = new ScanTask();
        activeTask.setId(5L);
        activeTask.setProjectId(1L);
        activeTask.setStatus("RUNNING");
        when(scanTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(activeTask));

        // 执行 & 验证
        BusinessException ex = assertThrows(BusinessException.class,
                () -> scanTaskService.createTask(1L));
        assertTrue(ex.getMessage().contains("已有扫描任务正在执行"),
                "错误信息应包含'已有扫描任务正在执行'");
        verify(scanTaskMapper, never()).insert(any());
        verify(scanTaskExecutorService, never()).executeScan(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("同一项目无活动任务时，应正常创建任务")
    void createTask_shouldSucceedWhenNoActiveTask() {
        // 准备：项目存在，无活动任务
        when(projectMapper.selectById(1L)).thenReturn(testProject);
        when(scanTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            ScanTask task = invocation.getArgument(0);
            task.setId(10L);
            return 1;
        }).when(scanTaskMapper).insert(any(ScanTask.class));

        // 执行
        ScanTaskDTO result = scanTaskService.createTask(1L);

        // 验证
        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        verify(scanTaskExecutorService).executeScan(eq(10L), anyString(), anyString());
    }

    // ==================== 查询任务测试 ====================

    @Test
    @DisplayName("查询任务详情时，应返回正确的任务信息")
    void getTask_shouldReturnCorrectTask() {
        // 准备
        when(scanTaskMapper.selectById(10L)).thenReturn(testTask);
        when(projectMapper.selectById(1L)).thenReturn(testProject);

        // 执行
        ScanTaskDTO result = scanTaskService.getTask(10L);

        // 验证
        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        assertEquals("test-project", result.getProjectName());
    }

    @Test
    @DisplayName("查询不存在的任务详情时，应抛出 BusinessException")
    void getTask_shouldThrowExceptionWhenNotExists() {
        // 准备
        when(scanTaskMapper.selectById(999L)).thenReturn(null);

        // 执行 & 验证
        assertThrows(BusinessException.class, () -> scanTaskService.getTask(999L));
    }

    // ==================== 扫描结果查询测试 ====================

    @Test
    @DisplayName("查询任务的扫描结果时，应返回结果列表")
    void getResults_shouldReturnResults() {
        // 准备
        ScanResult testResult = new ScanResult();
        testResult.setId(100L);
        testResult.setTaskId(10L);
        testResult.setDependencyName("log4j-core");
        testResult.setCveId("CVE-2021-44228");

        when(scanTaskMapper.selectById(10L)).thenReturn(testTask);
        when(scanResultMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(testResult));

        // 执行
        List<ScanResultDTO> results = scanTaskService.getResults(10L);

        // 验证
        assertEquals(1, results.size());
        assertEquals("log4j-core", results.get(0).getDependencyName());
        assertEquals("CVE-2021-44228", results.get(0).getCveId());
    }

    @Test
    @DisplayName("查询不存在的任务的扫描结果时，应抛出 BusinessException")
    void getResults_shouldThrowExceptionWhenTaskNotExists() {
        // 准备
        when(scanTaskMapper.selectById(999L)).thenReturn(null);

        // 执行 & 验证
        assertThrows(BusinessException.class, () -> scanTaskService.getResults(999L));
        verify(scanResultMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("任务没有扫描结果时，应返回空列表")
    void getResults_shouldReturnEmptyListWhenNoResults() {
        // 准备
        when(scanTaskMapper.selectById(10L)).thenReturn(testTask);
        when(scanResultMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // 执行
        List<ScanResultDTO> results = scanTaskService.getResults(10L);

        // 验证
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
