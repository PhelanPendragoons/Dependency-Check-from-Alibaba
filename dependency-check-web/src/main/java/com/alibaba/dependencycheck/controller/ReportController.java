package com.alibaba.dependencycheck.controller;

import com.alibaba.dependencycheck.model.vo.Result;
import com.alibaba.dependencycheck.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 报告管理接口
 * <p>
 * 提供扫描报告的查看和下载功能。
 * 报告由 DependencyCheck 引擎生成，格式为 HTML。
 * </p>
 *
 * <b>接口列表：</b>
 * <ul>
 *   <li>GET /api/reports/{taskId} — 查看/下载 HTML 报告</li>
 *   <li>GET /api/reports/{taskId}/exists — 检查报告是否存在</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 查看/下载 HTML 报告
     * <p>
     * 返回 DependencyCheck 引擎生成的完整 HTML 报告。
     * 浏览器会直接渲染 HTML 内容，方便用户查看。
     * </p>
     *
     * @param taskId 扫描任务 ID
     * @return HTML 报告内容
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<Resource> viewReport(@PathVariable Long taskId) {
        log.info("查看报告: taskId={}", taskId);

        Resource resource = reportService.getReportResource(taskId);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"dependency-check-report-" + taskId + ".html\"")
                .body(resource);
    }

    /**
     * 检查报告是否存在
     *
     * @param taskId 扫描任务 ID
     * @return true 如果报告文件存在
     */
    @GetMapping("/{taskId}/exists")
    public Result<Boolean> checkReportExists(@PathVariable Long taskId) {
        boolean exists = reportService.reportExists(taskId);
        return Result.success(exists);
    }
}
