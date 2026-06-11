package com.alibaba.dependencycheck.service;

import com.alibaba.dependencycheck.mapper.ScanResultMapper;
import com.alibaba.dependencycheck.mapper.ScanTaskMapper;
import com.alibaba.dependencycheck.model.entity.ScanResult;
import com.alibaba.dependencycheck.model.entity.ScanTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 扫描任务执行器
 * <p>
 * 独立于 ScanTaskService 的异步执行类，负责实际执行扫描任务。
 * 之所以独立成单独的 Service，是因为 Spring AOP 代理机制要求
 * {@code @Async} 注解必须通过代理对象调用才能生效。
 * 如果 {@code @Async} 方法定义在同一个类中并被内部方法直接调用，
 * Spring 的 AOP 代理将无法拦截，导致注解失效（同步执行）。
 * </p>
 *
 * <b>设计决策：</b>
 * <ul>
 *   <li>将"任务管理"（CRUD）与"任务执行"（扫描）分离到两个 Service 中</li>
 *   <li>ScanTaskService 负责创建/查询任务，调用本执行器异步执行</li>
 *   <li>本类专注处理扫描引擎的调用和结果持久化</li>
 * </ul>
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
public class ScanTaskExecutorService {

    private final ScanEngineService scanEngineService;
    private final ScanTaskMapper scanTaskMapper;
    private final ScanResultMapper scanResultMapper;

    /**
     * 异步执行扫描任务
     * <p>
     * 使用 {@code @Async("scanTaskExecutor")} 注解，方法会在独立的线程池中执行。
     * 扫描过程中会更新任务状态，扫描完成后将结果持久化到数据库。
     * </p>
     *
     * @param taskId       任务 ID
     * @param scanPath     待扫描的文件/目录路径
     * @param taskReportDir 报告输出目录
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
            ScanTask runningTask = new ScanTask();
            runningTask.setId(task.getId());
            runningTask.setStatus("RUNNING");
            runningTask.setStartedAt(LocalDateTime.now());
            scanTaskMapper.updateById(runningTask);


            // 执行扫描（调用 ScanEngineService）
            List<org.owasp.dependencycheck.dependency.Dependency> dependencies =
                    scanEngineService.scan(scanPath, taskReportDir);

            // 保存扫描结果（每个漏洞生成一条记录）
            int vulnCount = 0;
            for (org.owasp.dependencycheck.dependency.Dependency dep : dependencies) {
                List<ScanResult> results = convertToScanResults(taskId, dep);
                for (ScanResult result : results) {
                    scanResultMapper.insert(result);
                    if (result.getIsVulnerable()) {
                        vulnCount++;
                    }
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
     * 将 DependencyCheck 的 Dependency 对象转换为 ScanResult 实体列表
     * <p>
     * 每个漏洞生成一条独立的 ScanResult 记录，支持一个依赖有多个 CVE 的情况。
     * 如果没有漏洞，则生成一条无漏洞的记录。
     * </p>
     *
     * <b>v9.0.0 API 说明：</b>
     * <ul>
     *   <li>dep.getName() — 获取依赖名称</li>
     *   <li>dep.getVersion() — 获取依赖版本</li>
     *   <li>dep.getFilePath() — 获取文件路径</li>
     *   <li>dep.getVulnerabilities() — 获取漏洞集合（Set<Vulnerability>）</li>
     *   <li>dep.getLicense() — 获取许可证名称（String）</li>
     *   <li>vuln.getName() — 获取 CVE 编号</li>
     *   <li>vuln.getCvssV3() — 获取 CVSS v3 评分对象</li>
     *   <li>vuln.getCvssV2() — 获取 CVSS v2 评分对象</li>
     *   <li>vuln.getHighestSeverityText() — 获取严重等级</li>
     *   <li>vuln.getDescription() — 获取漏洞描述</li>
     * </ul>
     */
    private List<ScanResult> convertToScanResults(Long taskId,
                                                   org.owasp.dependencycheck.dependency.Dependency dep) {
        // 获取漏洞信息（v9.0.0 API）
        Set<org.owasp.dependencycheck.dependency.Vulnerability> vulnerabilities = dep.getVulnerabilities();

        // 获取许可证信息
        String license = dep.getLicense();

        if (vulnerabilities == null || vulnerabilities.isEmpty()) {
            // 没有漏洞，生成一条无漏洞记录
            ScanResult result = new ScanResult();
            result.setTaskId(taskId);
            result.setDependencyName(dep.getName());
            result.setDependencyVersion(dep.getVersion());
            result.setFilePath(dep.getFilePath());
            result.setIsVulnerable(false);
            if (license != null && !license.isEmpty()) {
                result.setLicenseName(license);
            }
            return List.of(result);
        }

        // 有漏洞，每个 CVE 生成一条记录
        return vulnerabilities.stream()
                .map(vuln -> {
                    ScanResult result = new ScanResult();
                    result.setTaskId(taskId);
                    result.setDependencyName(dep.getName());
                    result.setDependencyVersion(dep.getVersion());
                    result.setFilePath(dep.getFilePath());

                    // CVE 编号（vuln.getName() 返回如 "CVE-2021-44228"）
                    result.setCveId(vuln.getName());

                    // CVSS 评分（优先取 CVSS v3，没有则取 v2）
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

                    // 许可证信息
                    if (license != null && !license.isEmpty()) {
                        result.setLicenseName(license);
                    }

                    result.setIsVulnerable(true);
                    return result;
                })
                .toList();
    }

}
