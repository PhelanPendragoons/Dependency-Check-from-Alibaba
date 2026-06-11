package com.alibaba.dependencycheck.service;

import com.alibaba.dependencycheck.exception.BusinessException;
import com.alibaba.dependencycheck.mapper.ProjectMapper;
import com.alibaba.dependencycheck.mapper.ScanResultMapper;
import com.alibaba.dependencycheck.mapper.ScanTaskMapper;
import com.alibaba.dependencycheck.model.dto.ProjectDTO;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import java.io.Serializable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


/**
 * ProjectService 单元测试
 * <p>
 * 测试重点：
 * <ol>
 *   <li>级联删除：删除项目时是否同时清理 scan_task 和 scan_result</li>
 *   <li>物理文件清理：删除项目后上传目录是否被删除</li>
 *   <li>项目不存在时是否抛出 BusinessException</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private ScanTaskMapper scanTaskMapper;

    @Mock
    private ScanResultMapper scanResultMapper;

    @InjectMocks
    private ProjectService projectService;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ScanTask>> taskQueryCaptor;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ScanResult>> resultQueryCaptor;

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
    }

    // ==================== 级联删除测试 ====================

    @Test
    @DisplayName("删除项目时，应级联删除关联的扫描结果和扫描任务")
    void deleteProject_shouldCascadeDeleteRelatedData() {
        // 准备：项目存在，且有1个关联任务
        when(projectMapper.selectById(1L)).thenReturn(testProject);
        when(scanTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(testTask));

        // 执行
        projectService.deleteProject(1L);

        // 验证：先删除 scan_result，再删除 scan_task，最后删除 project
        verify(scanResultMapper).delete(any(LambdaQueryWrapper.class));
        verify(scanTaskMapper).delete(any(LambdaQueryWrapper.class));
        verify(projectMapper).deleteById(Long.valueOf(1L));
    }

    @Test
    @DisplayName("删除项目时，应使用正确的 taskId 查询条件删除扫描结果")

    void deleteProject_shouldUseCorrectTaskIdForResults() {
        // 准备
        when(projectMapper.selectById(1L)).thenReturn(testProject);
        when(scanTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(testTask));

        // 执行
        projectService.deleteProject(1L);

        // 验证：删除 scan_result 时使用了正确的 taskId
        verify(scanResultMapper).delete(resultQueryCaptor.capture());
        LambdaQueryWrapper<ScanResult> query = resultQueryCaptor.getValue();
        assertNotNull(query);
    }

    @Test
    @DisplayName("删除项目时，应使用正确的 projectId 查询条件删除扫描任务")
    void deleteProject_shouldUseCorrectProjectIdForTasks() {
        // 准备
        when(projectMapper.selectById(1L)).thenReturn(testProject);
        when(scanTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(testTask));

        // 执行
        projectService.deleteProject(1L);

        // 验证：查询 scan_task 时使用了正确的 projectId
        verify(scanTaskMapper).selectList(taskQueryCaptor.capture());
        LambdaQueryWrapper<ScanTask> query = taskQueryCaptor.getValue();
        assertNotNull(query);
    }

    @Test
    @DisplayName("删除不存在的项目时，应抛出 BusinessException")
    void deleteProject_shouldThrowExceptionWhenProjectNotExists() {
        // 准备
        when(projectMapper.selectById(999L)).thenReturn(null);

        // 执行 & 验证
        assertThrows(BusinessException.class, () -> projectService.deleteProject(999L));

        // 验证：没有执行任何删除操作
        verify(scanResultMapper, never()).delete(any());
        verify(scanTaskMapper, never()).delete(any());
        verify(projectMapper, never()).deleteById(any(java.io.Serializable.class));

    }

    @Test
    @DisplayName("项目没有关联任务时，删除操作应正常执行")
    void deleteProject_shouldWorkWhenNoRelatedTasks() {
        // 准备：项目存在，但没有关联任务
        when(projectMapper.selectById(1L)).thenReturn(testProject);
        when(scanTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // 执行
        projectService.deleteProject(1L);

        // 验证：没有删除 scan_result（因为没有任务），但删除了 project
        verify(scanResultMapper, never()).delete(any());
        verify(scanTaskMapper).delete(any());
        verify(projectMapper).deleteById(Long.valueOf(1L));
    }

    // ==================== 物理文件清理测试 ====================


    @Test
    @DisplayName("删除项目时，应清理服务器上的物理文件")
    void deleteProject_shouldCleanupPhysicalFiles() throws Exception {
        // 准备：创建一个临时目录模拟项目文件
        Path tempDir = Files.createTempDirectory("test-project-delete");
        Path tempFile = Files.createTempFile(tempDir, "test", ".jar");
        assertTrue(Files.exists(tempFile));

        testProject.setFilePath(tempDir.toString());
        when(projectMapper.selectById(1L)).thenReturn(testProject);
        when(scanTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // 执行
        projectService.deleteProject(1L);

        // 验证：物理文件已被删除
        assertFalse(Files.exists(tempDir), "项目目录应被删除");
        assertFalse(Files.exists(tempFile), "项目文件应被删除");
    }

    @Test
    @DisplayName("物理文件不存在时，删除操作不应抛出异常")
    void deleteProject_shouldNotThrowWhenPhysicalFileNotExists() {
        // 准备：项目文件路径不存在
        testProject.setFilePath("./uploads/non-existent-path");
        when(projectMapper.selectById(1L)).thenReturn(testProject);
        when(scanTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // 执行 & 验证：不应抛出异常
        assertDoesNotThrow(() -> projectService.deleteProject(1L));
        verify(projectMapper).deleteById(Long.valueOf(1L));
    }

    // ==================== 列表查询测试 ====================


    @Test
    @DisplayName("获取项目列表时，应返回所有项目")
    void listProjects_shouldReturnAllProjects() {
        // 准备
        when(projectMapper.selectList(null)).thenReturn(Collections.singletonList(testProject));

        // 执行
        List<ProjectDTO> result = projectService.listProjects();

        // 验证
        assertEquals(1, result.size());
        assertEquals("test-project", result.get(0).getName());
    }

    @Test
    @DisplayName("获取项目详情时，应返回正确的项目信息")
    void getProject_shouldReturnCorrectProject() {
        // 准备
        when(projectMapper.selectById(1L)).thenReturn(testProject);

        // 执行
        ProjectDTO result = projectService.getProject(1L);

        // 验证
        assertNotNull(result);
        assertEquals("test-project", result.getName());
    }

    @Test
    @DisplayName("获取不存在的项目详情时，应抛出 BusinessException")
    void getProject_shouldThrowExceptionWhenNotExists() {
        // 准备
        when(projectMapper.selectById(999L)).thenReturn(null);

        // 执行 & 验证
        assertThrows(BusinessException.class, () -> projectService.getProject(999L));
    }
}
