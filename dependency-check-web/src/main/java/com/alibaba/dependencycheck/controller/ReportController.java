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

import java.util.List;
import java.util.Map;

/**
 * 报告管理接口
 * <p>
 * 提供扫描报告的查看和下载功能，支持 HTML、Excel、PDF 三种格式。
 * </p>
 *
 * <b>接口列表：</b>
 * <ul>
 *   <li>GET /api/reports/{taskId} — 查看/下载报告（默认 HTML）</li>
 *   <li>GET /api/reports/{taskId}?format=excel — 下载 Excel 报告</li>
 *   <li>GET /api/reports/{taskId}?format=pdf — 下载 PDF 报告</li>
 *   <li>GET /api/reports/{taskId}/exists — 检查报告是否存在</li>
 *   <li>GET /api/reports/{taskId}/formats — 查询可用报告格式列表</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 查看/下载报告（支持多格式）
     * <p>
     * 通过 format 参数指定报告格式：
     * <ul>
     *   <li>{@code html} — 浏览器直接渲染 HTML（默认）</li>
     *   <li>{@code excel} — 下载 Excel 文件（.xlsx）</li>
     *   <li>{@code pdf} — 下载 PDF 文件</li>
     * </ul>
     * </p>
     *
     * <b>Content-Disposition 策略：</b>
     * <ul>
     *   <li>HTML 格式使用 {@code inline} — 浏览器直接预览</li>
     *   <li>Excel/PDF 格式使用 {@code attachment} — 强制下载</li>
     * </ul>
     *
     * @param taskId 扫描任务 ID
     * @param format 报告格式（html / excel / pdf，默认 html）
     * @return 报告文件内容
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<Resource> viewReport(
            @PathVariable Long taskId,
            @RequestParam(value = "format", defaultValue = "html") String format) {

        log.info("查看报告: taskId={}, format={}", taskId, format);

        // 根据格式获取报告资源
        Resource resource = reportService.getReportResource(taskId, format);

        // 根据格式设置 Content-Type 和 Content-Disposition
        MediaType contentType;
        String disposition;

        switch (format.toLowerCase()) {
            case "excel":
                contentType = MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                disposition = "attachment; filename=\"dependency-check-report-" + taskId + ".xlsx\"";
                break;
            case "pdf":
                contentType = MediaType.APPLICATION_PDF;
                disposition = "attachment; filename=\"dependency-check-report-" + taskId + ".pdf\"";
                break;
            default: // html
                contentType = MediaType.TEXT_HTML;
                disposition = "inline; filename=\"dependency-check-report-" + taskId + ".html\"";
                break;
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
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

    /**
     * 查询可用报告格式列表
     * <p>
     * 返回当前任务可用的报告格式列表，例如：
     * <pre>
     * {
     *   "taskId": 1,
     *   "formats": ["html", "excel", "pdf"]
     * }
     * </pre>
     * </p>
     *
     * @param taskId 扫描任务 ID
     * @return 可用格式列表
     */
    @GetMapping("/{taskId}/formats")
    public Result<Map<String, Object>> getAvailableFormats(@PathVariable Long taskId) {
        List<String> formats = reportService.getAvailableFormats(taskId);
        return Result.success(Map.of(
                "taskId", taskId,
                "formats", formats
        ));
    }
}
