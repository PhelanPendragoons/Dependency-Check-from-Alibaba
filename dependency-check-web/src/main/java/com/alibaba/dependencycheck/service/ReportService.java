package com.alibaba.dependencycheck.service;

import com.alibaba.dependencycheck.exception.BusinessException;
import com.alibaba.dependencycheck.mapper.ScanResultMapper;
import com.alibaba.dependencycheck.mapper.ScanTaskMapper;
import com.alibaba.dependencycheck.model.entity.ScanResult;
import com.alibaba.dependencycheck.model.entity.ScanTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 报告管理服务
 * <p>
 * 负责管理扫描生成的报告文件，支持 HTML、Excel、PDF 三种格式。
 * 提供报告的查询、生成和下载功能。
 * </p>
 *
 * <b>报告格式说明：</b>
 * <ul>
 *   <li>HTML — DependencyCheck 引擎原生生成，支持浏览器直接预览</li>
 *   <li>Excel — 使用 Apache POI 生成 .xlsx 格式，包含概览、漏洞详情、依赖清单三个工作表</li>
 *   <li>PDF — 使用 Flying Saucer 将 HTML 报告转换为 PDF 格式</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ScanTaskMapper scanTaskMapper;
    private final ScanResultMapper scanResultMapper;

    @Value("${app.report-dir:./reports}")
    private String reportDir;

    // ==================== 通用方法 ====================

    /**
     * 获取报告文件路径（HTML 格式）
     *
     * @param taskId 扫描任务 ID
     * @return 报告文件的绝对路径
     */
    public String getReportPath(Long taskId) {
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

    // ==================== 多格式报告支持 ====================

    /**
     * 按格式获取报告资源
     *
     * @param taskId 扫描任务 ID
     * @param format 报告格式（html / excel / pdf）
     * @return 报告文件的 Resource 对象
     */
    public Resource getReportResource(Long taskId, String format) {
        String filePath;
        switch (format.toLowerCase()) {
            case "html":
                filePath = getReportPath(taskId);
                break;
            case "excel":
                filePath = getExcelReportPath(taskId);
                break;
            case "pdf":
                filePath = getPdfReportPath(taskId);
                break;
            default:
                throw new BusinessException("不支持的报告格式: " + format);
        }
        return new FileSystemResource(filePath);
    }

    /**
     * 获取可用报告格式列表
     *
     * @param taskId 扫描任务 ID
     * @return 可用格式列表（html, excel, pdf）
     */
    public List<String> getAvailableFormats(Long taskId) {
        List<String> formats = new java.util.ArrayList<>();
        if (reportExists(taskId)) {
            formats.add("html");
        }
        if (Files.exists(Paths.get(getExcelReportPath(taskId)))) {
            formats.add("excel");
        }
        if (Files.exists(Paths.get(getPdfReportPath(taskId)))) {
            formats.add("pdf");
        }
        return formats;
    }

    // ==================== Excel 报告生成（P1-1） ====================

    /**
     * 生成 Excel 报告
     * <p>
     * 使用 Apache POI 生成 .xlsx 格式的扫描报告，包含三个工作表：
     * <ol>
     *   <li>概览（Summary）— 项目名称、扫描时间、依赖统计等</li>
     *   <li>漏洞详情（Vulnerabilities）— 每个 CVE 的详细信息</li>
     *   <li>依赖清单（Dependencies）— 所有依赖的完整清单</li>
     * </ol>
     * </p>
     *
     * @param taskId 扫描任务 ID
     * @return 生成的 Excel 文件路径
     */
    public String generateExcelReport(Long taskId) {
        // 1. 查询任务和结果数据
        ScanTask task = scanTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("扫描任务不存在: taskId=" + taskId);
        }

        List<ScanResult> results = scanResultMapper.findByTaskId(taskId);

        // 2. 创建 Excel 工作簿
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // 创建样式
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle criticalStyle = createSeverityStyle(workbook, IndexedColors.RED);
            CellStyle highStyle = createSeverityStyle(workbook, IndexedColors.RED);
            CellStyle mediumStyle = createSeverityStyle(workbook, IndexedColors.ORANGE);
            CellStyle lowStyle = createSeverityStyle(workbook, IndexedColors.YELLOW);

            // 3. 创建概览工作表
            createSummarySheet(workbook, task, titleStyle, dataStyle);

            // 4. 创建漏洞详情工作表
            createVulnerabilitySheet(workbook, results, headerStyle, dataStyle,
                    criticalStyle, highStyle, mediumStyle, lowStyle);

            // 5. 创建依赖清单工作表
            createDependencySheet(workbook, results, headerStyle, dataStyle);

            // 6. 写入文件
            String excelPath = getExcelReportPath(taskId);
            Path excelDir = Paths.get(excelPath).getParent();
            if (!Files.exists(excelDir)) {
                Files.createDirectories(excelDir);
            }
            try (OutputStream os = new FileOutputStream(excelPath)) {
                workbook.write(os);
            }

            log.info("Excel 报告生成成功: taskId={}, path={}", taskId, excelPath);
            return excelPath;

        } catch (Exception e) {
            log.error("Excel 报告生成失败: taskId={}", taskId, e);
            throw new BusinessException("Excel 报告生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取 Excel 报告文件路径
     */
    private String getExcelReportPath(Long taskId) {
        return reportDir + File.separator + taskId + File.separator
                + "dependency-check-report-" + taskId + ".xlsx";
    }

    /**
     * 创建概览工作表
     */
    private void createSummarySheet(XSSFWorkbook workbook, ScanTask task,
                                     CellStyle titleStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("概览");

        // 标题行
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("依赖安全扫描报告");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 1));

        // 数据行
        String[][] summaryData = {
                {"项目名称", task.getProjectId() != null ? task.getProjectId().toString() : "-"},
                {"扫描时间", task.getCompletedAt() != null ?
                        task.getCompletedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "-"},
                {"总依赖数", task.getTotalDependencies() != null ? task.getTotalDependencies().toString() : "0"},
                {"漏洞依赖数", task.getVulnerableDependencies() != null ? task.getVulnerableDependencies().toString() : "0"},
                {"扫描状态", task.getStatus() != null ? task.getStatus() : "-"}
        };

        for (int i = 0; i < summaryData.length; i++) {
            Row row = sheet.createRow(i + 2);
            Cell keyCell = row.createCell(0);
            keyCell.setCellValue(summaryData[i][0]);
            keyCell.setCellStyle(dataStyle);

            Cell valueCell = row.createCell(1);
            valueCell.setCellValue(summaryData[i][1]);
            valueCell.setCellStyle(dataStyle);
        }

        // 自动调整列宽
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    /**
     * 创建漏洞详情工作表
     */
    private void createVulnerabilitySheet(XSSFWorkbook workbook, List<ScanResult> results,
                                           CellStyle headerStyle, CellStyle dataStyle,
                                           CellStyle criticalStyle, CellStyle highStyle,
                                           CellStyle mediumStyle, CellStyle lowStyle) {
        Sheet sheet = workbook.createSheet("漏洞详情");

        // 表头行
        String[] headers = {"依赖名称", "版本", "CVE编号", "CVSS评分", "严重等级", "漏洞描述"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 筛选有漏洞的结果
        List<ScanResult> vulnerableResults = results.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsVulnerable()))
                .toList();

        // 数据行
        int rowNum = 1;
        for (ScanResult result : vulnerableResults) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(result.getDependencyName() != null ? result.getDependencyName() : "");
            row.createCell(1).setCellValue(result.getDependencyVersion() != null ? result.getDependencyVersion() : "");
            row.createCell(2).setCellValue(result.getCveId() != null ? result.getCveId() : "");
            row.createCell(3).setCellValue(result.getCvssScore() != null ? result.getCvssScore().toString() : "");

            // 严重等级（带条件格式）
            Cell severityCell = row.createCell(4);
            String severity = result.getSeverity() != null ? result.getSeverity() : "";
            severityCell.setCellValue(severity);
            if ("CRITICAL".equalsIgnoreCase(severity)) {
                severityCell.setCellStyle(criticalStyle);
            } else if ("HIGH".equalsIgnoreCase(severity)) {
                severityCell.setCellStyle(highStyle);
            } else if ("MEDIUM".equalsIgnoreCase(severity)) {
                severityCell.setCellStyle(mediumStyle);
            } else if ("LOW".equalsIgnoreCase(severity)) {
                severityCell.setCellStyle(lowStyle);
            } else {
                severityCell.setCellStyle(dataStyle);
            }

            row.createCell(5).setCellValue(result.getDescription() != null ? result.getDescription() : "");

            // 为其他单元格设置默认样式
            for (int i = 0; i < 6; i++) {
                if (i != 4) { // 跳过严重等级列（已设置样式）
                    Cell cell = row.getCell(i);
                    if (cell != null) {
                        cell.setCellStyle(dataStyle);
                    }
                }
            }
        }

        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * 创建依赖清单工作表
     */
    private void createDependencySheet(XSSFWorkbook workbook, List<ScanResult> results,
                                        CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("依赖清单");

        // 表头行
        String[] headers = {"依赖名称", "版本", "文件路径", "许可证", "是否有漏洞"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 按依赖名称去重
        List<ScanResult> distinctResults = results.stream()
                .collect(java.util.stream.Collectors.toMap(
                        r -> r.getDependencyName() + ":" + r.getDependencyVersion(),
                        r -> r,
                        (r1, r2) -> r1
                ))
                .values()
                .stream()
                .toList();

        // 数据行
        int rowNum = 1;
        for (ScanResult result : distinctResults) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(result.getDependencyName() != null ? result.getDependencyName() : "");
            row.createCell(1).setCellValue(result.getDependencyVersion() != null ? result.getDependencyVersion() : "");
            row.createCell(2).setCellValue(result.getFilePath() != null ? result.getFilePath() : "");
            row.createCell(3).setCellValue(result.getLicenseName() != null ? result.getLicenseName() : "");
            row.createCell(4).setCellValue(Boolean.TRUE.equals(result.getIsVulnerable()) ? "是" : "否");

            // 设置样式
            for (int i = 0; i < headers.length; i++) {
                row.getCell(i).setCellStyle(dataStyle);
            }
        }

        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // ==================== 单元格样式 ====================

    /**
     * 创建标题样式（加粗、大号字体）
     */
    private CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * 创建表头样式（加粗、蓝色背景）
     */
    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * 创建数据行样式
     */
    private CellStyle createDataStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    /**
     * 创建严重等级条件格式样式
     *
     * @param color 背景颜色（RED=高危, ORANGE=中危, YELLOW=低危）
     */
    private CellStyle createSeverityStyle(XSSFWorkbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // ==================== PDF 报告生成（P1-2） ====================

    /**
     * 生成 PDF 报告
     * <p>
     * 使用 Flying Saucer 库将已生成的 HTML 报告转换为 PDF 格式。
     * 基于 HTML 转换保证内容一致性，支持中文显示。
     * </p>
     *
     * @param taskId 扫描任务 ID
     * @return 生成的 PDF 文件路径
     */
    public String generatePdfReport(Long taskId) {
        // 1. 检查 HTML 报告是否存在
        String htmlPath = getReportPath(taskId);

        try {
            // 2. 读取 HTML 内容
            String htmlContent = Files.readString(Paths.get(htmlPath));

            // 3. 创建 PDF 渲染器
            ITextRenderer renderer = new ITextRenderer();

            // 4. 设置 HTML 内容
            renderer.setDocumentFromString(htmlContent);

            // 5. 布局并生成 PDF
            renderer.layout();

            // 6. 写入文件
            String pdfPath = getPdfReportPath(taskId);
            Path pdfDir = Paths.get(pdfPath).getParent();
            if (!Files.exists(pdfDir)) {
                Files.createDirectories(pdfDir);
            }

            try (OutputStream os = new FileOutputStream(pdfPath)) {
                renderer.createPDF(os);
            }

            log.info("PDF 报告生成成功: taskId={}, path={}", taskId, pdfPath);
            return pdfPath;

        } catch (Exception e) {
            log.error("PDF 报告生成失败: taskId={}", taskId, e);
            throw new BusinessException("PDF 报告生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取 PDF 报告文件路径
     */
    private String getPdfReportPath(Long taskId) {
        return reportDir + File.separator + taskId + File.separator
                + "dependency-check-report-" + taskId + ".pdf";
    }
}
