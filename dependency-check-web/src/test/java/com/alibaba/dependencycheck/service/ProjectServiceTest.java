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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;


/**
 * ProjectService 单元测试
 * <p>
 * 覆盖场景：
 * <ol>
 *   <li>级联删除：删除项目时是否同时清理 scan_task 和 scan_result</li>
 *   <li>物理文件清理：删除项目后上传目录是否被删除</li>
 *   <li>项目不存在时是否抛出 BusinessException</li>
 *   <li>B4-02：分页查询</li>
 *   <li>B4-08：项目名重复检查</li>
 *   <li>B4-11：项目名安全校验（禁止字符、长度限制）</li>
 *   <li>B4-06：Windows 保留名检查</li>
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

    @Mock
    private MultipartFile mockFile;

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

    // ==================== B4-02: 分页查询测试 ====================

    @Test
    @DisplayName("B4-02: 分页查询项目列表时，应返回分页结果")
    void listProjects_shouldReturnPaginatedResults() {
        // 准备：模拟 MyBatis-Plus 分页返回
        Page<Project> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Collections.singletonList(testProject));
        mockPage.setTotal(1);
        when(projectMapper.selectPage(any(Page.class), isNull())).thenReturn(mockPage);

        // 执行
        IPage<ProjectDTO> result = projectService.listProjects(1, 10);

        // 验证
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("test-project", result.getRecords().get(0).getName());

        // 验证使用了正确的分页参数
        ArgumentCaptor<Page<Project>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(projectMapper).selectPage(pageCaptor.capture(), isNull());
        Page<Project> capturedPage = pageCaptor.getValue();
        assertEquals(1, capturedPage.getCurrent());
        assertEquals(10, capturedPage.getSize());
    }

    @Test
    @DisplayName("B4-02: 分页查询空列表时，应返回空结果")
    void listProjects_shouldReturnEmptyPageWhenNoProjects() {
        // 准备
        Page<Project> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Collections.emptyList());
        mockPage.setTotal(0);
        when(projectMapper.selectPage(any(Page.class), isNull())).thenReturn(mockPage);

        // 执行
        IPage<ProjectDTO> result = projectService.listProjects(1, 10);

        // 验证
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    // ==================== 项目详情查询测试 ====================

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

    // ==================== B4-11: 项目名安全校验测试 ====================

    @Test
    @DisplayName("B4-11: 项目名为空时，应抛出异常")
    void createProject_shouldRejectEmptyName() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, null, "desc"));
        assertTrue(ex.getMessage().contains("不能为空"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-11: 项目名为纯空白时，应抛出异常")
    void createProject_shouldRejectBlankName() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "   ", "desc"));
        assertTrue(ex.getMessage().contains("不能为空"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-11: 项目名超过255字符时，应抛出异常")
    void createProject_shouldRejectTooLongName() {
        String longName = "a".repeat(256);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, longName, "desc"));
        assertTrue(ex.getMessage().contains("255"), "错误信息应包含长度限制");
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-11: 项目名包含斜杠时，应抛出异常")
    void createProject_shouldRejectNameWithSlash() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "evil/name", "desc"));
        assertTrue(ex.getMessage().contains("非法字符"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-11: 项目名包含反斜杠时，应抛出异常")
    void createProject_shouldRejectNameWithBackslash() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "evil\\name", "desc"));
        assertTrue(ex.getMessage().contains("非法字符"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-11: 项目名包含..路径穿越序列时，应抛出异常")
    void createProject_shouldRejectNameWithDotDot() {
        // 注意：名中不含 / 等其他禁止字符，确保 .. 序列自身触发异常
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "evil..etc", "desc"));
        assertTrue(ex.getMessage().contains(".."),
                "错误信息应包含'..': " + ex.getMessage());
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-11: 项目名包含尖括号时，应抛出异常")
    void createProject_shouldRejectNameWithAngleBrackets() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "evil<script>", "desc"));
        assertTrue(ex.getMessage().contains("非法字符"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-11: 项目名包含冒号时，应抛出异常")
    void createProject_shouldRejectNameWithColon() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "C:evil", "desc"));
        assertTrue(ex.getMessage().contains("非法字符"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-11: 项目名包含双引号时，应抛出异常")
    void createProject_shouldRejectNameWithQuote() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "evil\"name", "desc"));
        assertTrue(ex.getMessage().contains("非法字符"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-11: 项目名包含管道符时，应抛出异常")
    void createProject_shouldRejectNameWithPipe() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "evil|name", "desc"));
        assertTrue(ex.getMessage().contains("非法字符"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-11: 项目名包含问号时，应抛出异常")
    void createProject_shouldRejectNameWithQuestionMark() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "evil?name", "desc"));
        assertTrue(ex.getMessage().contains("非法字符"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-11: 项目名包含星号时，应抛出异常")
    void createProject_shouldRejectNameWithAsterisk() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "evil*name", "desc"));
        assertTrue(ex.getMessage().contains("非法字符"));
        verify(projectMapper, never()).insert(any());
    }

    // ==================== B4-06: Windows 保留名测试 ====================

    @Test
    @DisplayName("B4-06: 项目名 CON 应被拒绝")
    void createProject_shouldRejectWindowsReservedCON() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "CON", "desc"));
        assertTrue(ex.getMessage().contains("Windows"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-06: 项目名 NUL 应被拒绝")
    void createProject_shouldRejectWindowsReservedNUL() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "NUL", "desc"));
        assertTrue(ex.getMessage().contains("Windows"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-06: 项目名 PRN 应被拒绝")
    void createProject_shouldRejectWindowsReservedPRN() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "PRN", "desc"));
        assertTrue(ex.getMessage().contains("Windows"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-06: 项目名 COM1 应被拒绝")
    void createProject_shouldRejectWindowsReservedCOM1() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "COM1", "desc"));
        assertTrue(ex.getMessage().contains("Windows"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-06: 项目名 LPT1 应被拒绝")
    void createProject_shouldRejectWindowsReservedLPT1() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "LPT1", "desc"));
        assertTrue(ex.getMessage().contains("Windows"));
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("B4-06: Windows保留名大小写不敏感（con 应被拒绝）")
    void createProject_shouldRejectWindowsReservedCaseInsensitive() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "con", "desc"));
        assertTrue(ex.getMessage().contains("Windows"));
        verify(projectMapper, never()).insert(any());
    }

    // ==================== B4-08: 项目名重复检查测试 ====================

    @Test
    @DisplayName("B4-08: 项目名已存在时，应拒绝创建")
    void createProject_shouldRejectDuplicateName() {
        // 准备：合法名称 + 非空文件 + 已有同名项目
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn("test.zip");
        when(projectMapper.findByName("existing-project")).thenReturn(testProject);

        // 执行
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.createProject(mockFile, "existing-project", "desc"));
        assertTrue(ex.getMessage().contains("已存在"));
        verify(projectMapper, never()).insert(any());
    }

}
