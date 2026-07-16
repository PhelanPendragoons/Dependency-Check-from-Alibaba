package com.alibaba.dependencycheck.controller;

import com.alibaba.dependencycheck.mapper.ProjectMapper;
import com.alibaba.dependencycheck.mapper.ScanResultMapper;
import com.alibaba.dependencycheck.mapper.ScanTaskMapper;
import com.alibaba.dependencycheck.model.entity.ScanResult;
import com.alibaba.dependencycheck.model.entity.ScanTask;
import com.alibaba.dependencycheck.model.vo.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仪表盘数据接口
 * <p>
 * 提供仪表盘首页所需的聚合统计数据，包括项目总数、任务总数、
 * 漏洞总数及其按严重等级的分布（CRITICAL / HIGH / MEDIUM / LOW）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProjectMapper projectMapper;
    private final ScanTaskMapper scanTaskMapper;
    private final ScanResultMapper scanResultMapper;

    /**
     * 获取仪表盘统计数据
     * <p>
     * 返回聚合统计：项目数、任务数、漏洞总数及按严重等级的分布。
     * 漏洞统计基于所有已完成任务的扫描结果（is_vulnerable = true）。
     * </p>
     *
     * @return 统计数据 Map
     */
    @GetMapping
    public Result<Map<String, Object>> getStats() {
        // 1. 项目总数
        long totalProjects = projectMapper.selectCount(null);

        // 2. 任务总数
        long totalTasks = scanTaskMapper.selectCount(null);

        // 3. 漏洞统计：查询所有 is_vulnerable=true 的扫描结果
        LambdaQueryWrapper<ScanResult> vulnQuery = new LambdaQueryWrapper<>();
        vulnQuery.eq(ScanResult::getIsVulnerable, true);
        List<ScanResult> vulnerabilities = scanResultMapper.selectList(vulnQuery);

        long totalVulnerabilities = vulnerabilities.size();

        // 4. 按严重等级分组统计
        Map<String, Long> severityCounts = vulnerabilities.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getSeverity() != null ? r.getSeverity().toUpperCase() : "UNKNOWN",
                        Collectors.counting()
                ));

        long criticalCount = severityCounts.getOrDefault("CRITICAL", 0L);
        long highCount = severityCounts.getOrDefault("HIGH", 0L);
        long mediumCount = severityCounts.getOrDefault("MEDIUM", 0L);
        long lowCount = severityCounts.getOrDefault("LOW", 0L);

        // 5. 最近完成的任务数
        LambdaQueryWrapper<ScanTask> completedQuery = new LambdaQueryWrapper<>();
        completedQuery.eq(ScanTask::getStatus, "COMPLETED");
        long completedTasks = scanTaskMapper.selectCount(completedQuery);

        // 6. 许可证问题数（license_name 不为空且非 vulnerabilities 的结果）
        LambdaQueryWrapper<ScanResult> licenseQuery = new LambdaQueryWrapper<>();
        licenseQuery.isNotNull(ScanResult::getLicenseName)
                .ne(ScanResult::getLicenseName, "")
                .eq(ScanResult::getIsVulnerable, false);
        long licenseIssues = scanResultMapper.selectCount(licenseQuery);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalProjects", totalProjects);
        stats.put("totalTasks", totalTasks);
        stats.put("completedTasks", completedTasks);
        stats.put("totalVulnerabilities", totalVulnerabilities);
        stats.put("criticalCount", criticalCount);
        stats.put("highCount", highCount);
        stats.put("mediumCount", mediumCount);
        stats.put("lowCount", lowCount);
        stats.put("licenseIssues", licenseIssues);

        log.info("仪表盘统计: projects={}, tasks={}, vulnerabilities={}, critical={}, high={}, medium={}, low={}",
                totalProjects, totalTasks, totalVulnerabilities, criticalCount, highCount, mediumCount, lowCount);

        return Result.success(stats);
    }
}
