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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 扫描任务管理服务
 * <p>
 * 负责创建、执行、跟踪扫描任务。
 * 扫描任务使用 @Async 注解异步执行，避免阻塞 HTTP 请求线程。
 * </p>
 *
 * <b>v9.0.0 API 说明：</b>
 * <ul>
 *   <li>dep.getVulnerabilities() — 获取漏洞信息（返回 Set<Vulnerability>）</li>
 *   <li>dep.getLicense() — 获取许可证名称（返回 String）</li>
 *   <li>vuln.getName() — 获取 CVE 编号（如 CVE-2021-44228）</li>
 *   <li>vuln.getCvssV3() / getCvssV2() — 获取 CVSS 评分对象</li>
 *   <li>vuln.getHighestSeverityText() — 获取严重等级文本</li>
 *   <li>vuln.getDescription() — 获取漏洞描述</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanTaskService {

    private final ScanEngineService scanEngineService;
    private final ScanTaskMapper scanTaskMapper;
    private final ScanResultMapper scanResultMapper;
    private final ProjectMapper projectMapper;

    @Value("${app.report-dir:./reports}")
    private String reportDir;

    /**
     * 创建扫描任务
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

        // 2. 创建扫描任务记录
        ScanTask task = new ScanTask();
        task.setProjectId(projectId);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setTotalDependencies(0);
        task.setVulnerableDependencies(0);
        scanTaskMapper.insert(task);

        log.info("扫描任务创建成功: taskId={}, projectId={}", task.getId(), projectId);

        // 3. 异步执行扫描
        String taskReportDir = reportDir + "/" + task.getId();
        executeScan(task.getId(), project.getFilePath(), taskReportDir);

        return convertToDTO(task);
    }

    /**
     * 异步执行扫描任务
     * <p>
     * 使用 @Async 注解，方法会在独立的线程池中执行。
     * 扫描过程中会更新任务状态和进度。
     * </p>
     */
    @Async("scanTaskExecutor")
    public void executeScan(Long taskId, String scanPath, String taskReportDir) {
        ScanTask task = scanTaskMapper.selectById(taskId);
        if (task == null) {
            log.error("任务不存在: {}", taskId);
            return;
        }

        try {
            // 更新任务状态为"运行中"
            task.setStatus("RUNNING");
            task.setStartedAt(LocalDateTime.now());
            scanTaskMapper.updateById(task);

            // 执行扫描（调用 ScanEngineService）
            List<org.owasp.dependencycheck.dependency.Dependency> dependencies =
                    scanEngineService.scan(scanPath, taskReportDir);

            // 保存扫描结果
            int vulnCount = 0;
            for (org.owasp.dependencycheck.dependency.Dependency dep : dependencies) {
                ScanResult result = convertToScanResult(taskId, dep);
                scanResultMapper.insert(result);
                if (result.getIsVulnerable()) {
                    vulnCount++;
                }
            }

            // 更新任务状态为"完成"
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setTotalDependencies(dependencies.size());
            task.setVulnerableDependencies(vulnCount);
            task.setCompletedAt(LocalDateTime.now());
            scanTaskMapper.updateById(task);

            log.info("扫描任务 {} 完成，共 {} 个依赖，{} 个有漏洞", taskId, dependencies.size(), vulnCount);

        } catch (Exception e) {
            log.error("扫描任务 {} 失败", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            scanTaskMapper.updateById(task);
        }
    }

    /**
     * 获取任务状态
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
     * 将 DependencyCheck 的 Dependency 对象转换为自定义的 ScanResult 实体
     * <p>
     * v9.0.0 API 说明：
     * - dep.getName() — 获取依赖名称
     * - dep.getVersion() — 获取依赖版本
     * - dep.getFilePath() — 获取文件路径
     * - dep.getVulnerabilities() — 获取漏洞集合（Set<Vulnerability>）
     * - dep.getLicense() — 获取许可证名称（String）
     * - vuln.getName() — 获取 CVE 编号
     * - vuln.getCvssV3() — 获取 CVSS v3 评分对象
     * - vuln.getCvssV2() — 获取 CVSS v2 评分对象
     * - vuln.getHighestSeverityText() — 获取严重等级
     * - vuln.getDescription() — 获取漏洞描述
     * </p>
     */
    private ScanResult convertToScanResult(Long taskId,
                                           org.owasp.dependencycheck.dependency.Dependency dep) {
        ScanResult result = new ScanResult();
        result.setTaskId(taskId);
        result.setDependencyName(dep.getName());
        result.setDependencyVersion(dep.getVersion());
        result.setFilePath(dep.getFilePath());

        // 获取漏洞信息（v9.0.0 API）
        // getVulnerabilities() 返回 Set<Vulnerability>
        Set<org.owasp.dependencycheck.dependency.Vulnerability> vulnerabilities = dep.getVulnerabilities();
        if (vulnerabilities != null && !vulnerabilities.isEmpty()) {
            // 取第一个漏洞信息
            org.owasp.dependencycheck.dependency.Vulnerability vuln = vulnerabilities.iterator().next();

            // CVE 编号（vuln.getName() 返回如 "CVE-2021-44228"）
            result.setCveId(vuln.getName());

            // CVSS 评分（优先取 CVSS v3，没有则取 v2）
            // CvssV3.getCvssData().getBaseScore() 获取基础评分
            // CvssV2.getCvssData().getBaseScore() 获取基础评分
            io.github.jeremylong.openvulnerability.client.nvd.CvssV3 cvssV3 = vuln.getCvssV3();
            if (cvssV3 != null && cvssV3.getCvssData() != null) {
                result.setCvssScore(BigDecimal.valueOf(cvssV3.getCvssData().getBaseScore()));
            } else {
                io.github.jeremylong.openvulnerability.client.nvd.CvssV2 cvssV2 = vuln.getCvssV2();
                if (cvssV2 != null && cvssV2.getCvssData() != null) {
                    result.setCvssScore(BigDecimal.valueOf(cvssV2.getCvssData().getBaseScore()));
                }
            }

            // 严重等级
            result.setSeverity(vuln.getHighestSeverityText());

            // 漏洞描述
            result.setDescription(vuln.getDescription());

            result.setIsVulnerable(true);
        }

        // 如果没有漏洞，标记为非漏洞依赖
        if (result.getIsVulnerable() == null) {
            result.setIsVulnerable(false);
        }

        // 获取许可证信息（v9.0.0 API）
        // getLicense() 返回 String，可能为 null
        String license = dep.getLicense();
        if (license != null && !license.isEmpty()) {
            result.setLicenseName(license);
        }

        return result;
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
