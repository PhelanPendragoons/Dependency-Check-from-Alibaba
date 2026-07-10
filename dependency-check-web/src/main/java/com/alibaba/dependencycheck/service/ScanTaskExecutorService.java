package com.alibaba.dependencycheck.service;

import com.alibaba.dependencycheck.mapper.ScanResultMapper;
import com.alibaba.dependencycheck.mapper.ScanTaskMapper;
import com.alibaba.dependencycheck.model.entity.ScanResult;
import com.alibaba.dependencycheck.model.entity.ScanTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
     * OSS 服务（可选注入）。
     * <p>
     * D4-06（7/8）：当 {@code app.oss.enabled=false} 时，
     * {@link com.alibaba.dependencycheck.config.OssConfig} 不创建 Bean，
     * 此字段为 {@code null}，所有 OSS 相关逻辑自动跳过。
     * </p>
     */
    @Autowired(required = false)
    private OssService ossService;

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
    /**
     * P1#105：任务取消请求标记表。
     * <p>
     * Key = taskId, Value = true（已请求取消）。
     * executeScan 在关键节点检查此标记，若已取消则跳过结果持久化。
     * 任务完成/失败后从表中移除。
     * </p>
     */
    private final ConcurrentHashMap<Long, Boolean> cancelRequests = new ConcurrentHashMap<>();

    /** B3-06: 通用错误码，用于脱敏处理 */
    private static final String SCAN_ERROR_PREFIX = "SCAN_ERR";

    @Async("scanTaskExecutor")
    public void executeScan(Long taskId, String scanPath, String taskReportDir) {
        ScanTask task = scanTaskMapper.selectById(taskId);
        // B3-10 修复：任务不存在时记录 WARN 日志（区分于系统异常），让调用方可通过日志感知
        if (task == null) {
            log.warn("扫描任务不存在，可能已被删除或 ID 无效: taskId={}", taskId);
            return;
        }

        try {
            // P1#105: 检查是否在执行前已被取消（PENDING 状态时被取消）
            if (cancelRequests.containsKey(taskId)) {
                log.info("扫描任务 {} 已被取消，跳过执行", taskId);
                cancelRequests.remove(taskId);
                return;
            }

            // B3-09 修复：更新任务状态为"运行中"，进度设为 10%（开始扫描）
            ScanTask runningTask = new ScanTask();
            runningTask.setId(task.getId());
            runningTask.setStatus("RUNNING");
            runningTask.setProgress(10);
            runningTask.setStartedAt(LocalDateTime.now());
            scanTaskMapper.updateById(runningTask);

            // 执行扫描（调用 ScanEngineService）
            List<org.owasp.dependencycheck.dependency.Dependency> dependencies =
                    scanEngineService.scan(scanPath, taskReportDir);

            // P1#105: 扫描完成后检查取消标记，跳过结果持久化
            if (cancelRequests.containsKey(taskId)) {
                log.info("扫描任务 {} 在扫描完成后被取消，跳过结果保存", taskId);
                // OSS 报告仍上传（已完成，无需回滚），但结果不入库
                cancelRequests.remove(taskId);
                return;
            }

            // D4-06（7/8）：扫描完成后上传 HTML 报告到 OSS
            // OSS 上传失败不阻塞扫描流程 — 本地文件已存在作为降级兜底
            uploadHtmlReportToOss(taskId, taskReportDir);

            // B3-09 修复：扫描完成后进度更新为 90%（保存结果中）
            ScanTask progressTask = new ScanTask();
            progressTask.setId(task.getId());
            progressTask.setProgress(90);
            scanTaskMapper.updateById(progressTask);

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
            // P1#105: 先读 DB 确认当前状态 — 若已被取消则不覆盖为 FAILED
            ScanTask currentTask = scanTaskMapper.selectById(taskId);
            if (currentTask != null && "CANCELLED".equals(currentTask.getStatus())) {
                log.info("扫描任务 {} 已被取消（DB 状态为 CANCELLED），跳过 FAILED 覆盖", taskId);
                cancelRequests.remove(taskId);
                return;
            }
            log.error("扫描任务 {} 失败", taskId, e);
            task.setStatus("FAILED");
            // B3-06 修复：脱敏处理 — 不直接暴露原始异常消息（可能含内部路径）
            // 使用固定 errorCode 替代，完整异常信息仅记录在日志中
            task.setErrorMessage(SCAN_ERROR_PREFIX + "_" + taskId + ": 扫描执行失败，请查看系统日志");
            task.setCompletedAt(LocalDateTime.now());
            scanTaskMapper.updateById(task);
        } finally {
            // P1#105: 清理取消标记
            cancelRequests.remove(taskId);
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

    /**
     * 上传 HTML 报告到 OSS（D4-06，7/8）
     * <p>
     * OSS 未启用或上传失败时静默返回，不抛出异常，确保扫描主流程不受影响。
     * 本地文件作为降级兜底始终可用。
     * </p>
     *
     * @param taskId        扫描任务 ID
     * @param taskReportDir 报告输出目录（HTML 报告由 engine.writeReports() 生成在此）
     */
    private void uploadHtmlReportToOss(Long taskId, String taskReportDir) {
        if (ossService == null || !ossService.isAvailable()) {
            return;
        }

        Path htmlFile = Paths.get(taskReportDir, "dependency-check-report.html");
        if (!Files.exists(htmlFile)) {
            log.debug("HTML 报告文件不存在，跳过 OSS 上传: {}", htmlFile);
            return;
        }

        try (FileInputStream fis = new FileInputStream(htmlFile.toFile())) {
            String key = ossService.buildReportKey(taskId, "dependency-check-report.html");
            boolean uploaded = ossService.upload(fis, key);
            if (uploaded) {
                log.info("HTML 报告已上传至 OSS: taskId={}, key={}", taskId, key);
            }
        } catch (Exception e) {
            log.warn("OSS 上传 HTML 报告失败（不影响扫描结果，本地文件可用作降级）: taskId={}", taskId, e);
        }
    }

    /**
     * P1#105：请求取消指定的扫描任务。
     * <p>
     * 取消逻辑：
     * <ol>
     *   <li>设置 cancelRequests 标记，executeScan 在关键节点检查此标记</li>
     *   <li>更新 DB 状态为 CANCELLED（仅当当前状态为 PENDING 或 RUNNING）</li>
     *   <li>executeScan 的 catch 块读取 DB 状态，CANCELLED 时不覆盖为 FAILED</li>
     * </ol>
     * </p>
     * <p>
     * <b>注意：</b>此方法不强制中断执行中的扫描线程（OWASP Engine 不支持中断），
     * 而是通过标记+状态保护实现"逻辑取消"。扫描可能继续在后台运行到完成，
     * 但结果不会被持久化，DB 状态保持 CANCELLED。
     * </p>
     *
     * @param taskId 要取消的任务 ID
     * @return true 如果取消请求已发出
     */
    public boolean cancelScan(Long taskId) {
        ScanTask task = scanTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("取消扫描失败：任务不存在 taskId={}", taskId);
            return false;
        }

        String currentStatus = task.getStatus();
        if (!"PENDING".equals(currentStatus) && !"RUNNING".equals(currentStatus)) {
            log.warn("取消扫描失败：任务状态不允许取消 taskId={}, status={}", taskId, currentStatus);
            return false;
        }

        // 设置取消标记
        cancelRequests.put(taskId, true);
        log.info("已设置取消标记: taskId={}", taskId);

        // 更新 DB 状态为 CANCELLED
        ScanTask update = new ScanTask();
        update.setId(taskId);
        update.setStatus("CANCELLED");
        update.setCompletedAt(LocalDateTime.now());
        scanTaskMapper.updateById(update);
        log.info("扫描任务已取消: taskId={}", taskId);

        return true;
    }

}
