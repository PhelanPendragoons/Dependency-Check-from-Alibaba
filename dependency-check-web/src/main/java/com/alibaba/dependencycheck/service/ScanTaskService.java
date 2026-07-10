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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 扫描任务管理服务
 * <p>
 * 负责创建、查询扫描任务。
 * 实际的扫描执行委托给 {@link ScanTaskExecutorService} 异步执行，
 * 这是为了确保 {@code @Async} 注解通过 Spring AOP 代理生效。
 * </p>
 *
 * <b>职责分离：</b>
 * <ul>
 *   <li>ScanTaskService — 任务 CRUD（创建、查询状态、查询结果）</li>
 *   <li>ScanTaskExecutorService — 任务执行（调用扫描引擎、持久化结果）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanTaskService {

    private final ScanTaskExecutorService scanTaskExecutorService;
    private final ScanTaskMapper scanTaskMapper;
    private final ScanResultMapper scanResultMapper;
    private final ProjectMapper projectMapper;

    @Value("${app.report-dir:./reports}")
    private String reportDir;

    /**
     * 创建扫描任务
     * <p>
     * 创建任务记录后，委托 {@link ScanTaskExecutorService#executeScan()} 异步执行扫描。
     * 方法立即返回任务信息，前端可通过轮询 {@link #getTask(Long)} 获取扫描进度。
     * </p>
     *
     * @param projectId 项目 ID
     * @return 扫描任务 DTO
     */
    public ScanTaskDTO createTask(Long projectId) {
        // 1. 检查项目是否存在
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在: " + projectId);
        }

        // B3-07 + B3-05 修复：检查同一 projectId 是否已有 RUNNING 或 PENDING 任务
        LambdaQueryWrapper<ScanTask> activeTaskQuery = new LambdaQueryWrapper<>();
        activeTaskQuery.eq(ScanTask::getProjectId, projectId)
                .in(ScanTask::getStatus, "RUNNING", "PENDING");
        List<ScanTask> activeTasks = scanTaskMapper.selectList(activeTaskQuery);
        if (!activeTasks.isEmpty()) {
            throw new BusinessException("该项目已有扫描任务正在执行或等待中，请等待当前任务完成后再提交");
        }

        // 2. 创建扫描任务记录
        ScanTask task = new ScanTask();
        task.setProjectId(projectId);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setTotalDependencies(0);
        task.setVulnerableDependencies(0);
        scanTaskMapper.insert(task);

        log.info("扫描任务创建成功: taskId={}, projectId={}", task.getId(), projectId);

        // 3. 委托 ScanTaskExecutorService 异步执行扫描
        //    注意：这里通过注入的代理对象调用，@Async 注解才能生效
        String taskReportDir = reportDir + "/" + task.getId();
        scanTaskExecutorService.executeScan(task.getId(), project.getFilePath(), taskReportDir);

        return convertToDTO(task);
    }

    /**
     * P1#105：取消扫描任务。
     * <p>
     * 仅 PENDING 或 RUNNING 状态的任务可以被取消。
     * 委托 {@link ScanTaskExecutorService#cancelScan(Long)} 执行实际的取消逻辑。
     * </p>
     *
     * @param taskId 任务 ID
     * @return 更新后的任务 DTO
     * @throws BusinessException 如果任务不存在或状态不允许取消
     */
    public ScanTaskDTO cancelTask(Long taskId) {
        ScanTask task = scanTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }

        String status = task.getStatus();
        if (!"PENDING".equals(status) && !"RUNNING".equals(status)) {
            throw new BusinessException("当前状态不允许取消: " + status + "（仅 PENDING 或 RUNNING 可取消）");
        }

        boolean cancelled = scanTaskExecutorService.cancelScan(taskId);
        if (!cancelled) {
            throw new BusinessException("取消任务失败: " + taskId);
        }

        // 重新查询获取最新状态
        ScanTask updated = scanTaskMapper.selectById(taskId);
        return convertToDTO(updated);
    }

    /**
     * 获取任务状态
     *
     * @param id 任务 ID
     * @return 任务状态信息（包含进度百分比、项目名称等）
     */
    public ScanTaskDTO getTask(Long id) {
        ScanTask task = scanTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在: " + id);
        }
        ScanTaskDTO dto = convertToDTO(task);

        // 补充项目名称
        Project project = projectMapper.selectById(task.getProjectId());
        if (project != null) {
            dto.setProjectName(project.getName());
        }

        return dto;
    }

    /**
     * 获取扫描结果列表
     *
     * @param taskId 任务 ID
     * @return 扫描结果列表（每个依赖的漏洞和许可证信息）
     */
    public List<ScanResultDTO> getResults(Long taskId) {
        // 检查任务是否存在
        ScanTask task = scanTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }

        // 使用 MyBatis-Plus QueryWrapper 按 taskId 查询，避免全表扫描
        LambdaQueryWrapper<ScanResult> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ScanResult::getTaskId, taskId);
        return scanResultMapper.selectList(queryWrapper)
                .stream()
                .map(this::convertToResultDTO)
                .collect(Collectors.toList());
    }

    /**
     * 将实体类转换为 DTO
     */
    private ScanTaskDTO convertToDTO(ScanTask task) {
        ScanTaskDTO dto = new ScanTaskDTO();
        BeanUtils.copyProperties(task, dto);
        return dto;
    }

    /**
     * 将实体类转换为结果 DTO
     */
    private ScanResultDTO convertToResultDTO(ScanResult result) {
        ScanResultDTO dto = new ScanResultDTO();
        BeanUtils.copyProperties(result, dto);
        return dto;
    }
}
