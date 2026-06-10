package com.alibaba.dependencycheck.service;

import com.alibaba.dependencycheck.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 报告管理服务
 * <p>
 * 负责管理扫描生成的 HTML 报告文件，提供报告的查询和下载功能。
 * </p>
 */
@Slf4j
@Service
public class ReportService {

    @Value("${app.report-dir:./reports}")
    private String reportDir;

    /**
     * 获取报告文件路径
     *
     * @param taskId 扫描任务 ID
     * @return 报告文件的绝对路径
     */
    public String getReportPath(Long taskId) {
        // 报告目录结构: {reportDir}/{taskId}/dependency-check-report.html
        String reportFilePath = reportDir + File.separator + taskId + File.separator + "dependency-check-report.html";
        Path path = Paths.get(reportFilePath);

        if (!Files.exists(path)) {
            throw new BusinessException("报告文件不存在: taskId=" + taskId);
        }

        return path.toAbsolutePath().toString();
    }

    /**
     * 获取报告文件资源（用于下载）
     *
     * @param taskId 扫描任务 ID
     * @return 报告文件的 Resource 对象
     */
    public Resource getReportResource(Long taskId) {
        String reportPath = getReportPath(taskId);
        return new FileSystemResource(reportPath);
    }

    /**
     * 检查报告是否存在
     *
     * @param taskId 扫描任务 ID
     * @return true 如果报告文件存在
     */
    public boolean reportExists(Long taskId) {
        String reportFilePath = reportDir + File.separator + taskId + File.separator + "dependency-check-report.html";
        return Files.exists(Paths.get(reportFilePath));
    }
}
