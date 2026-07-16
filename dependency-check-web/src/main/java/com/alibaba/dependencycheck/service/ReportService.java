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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    /**
     * OSS 服务（可选注入）。
     * <p>
     * D4-07（7/8）：当 {@code app.oss.enabled=false} 时，
     * {@link com.alibaba.dependencycheck.config.OssConfig} 不创建 Bean，
     * 此字段为 {@code null}，所有 OSS 逻辑自动跳过，走纯本地存储。
     * </p>
     */
    @Autowired(required = false)
    private OssService ossService;

    // ==================== 通用方法 ====================

    /**
     * 获取报告文件路径（HTML 格式）
     *
     * @param taskId 扫描任务 ID
     * @return 报告文件的绝对路径
     */
    public String getReportPath(Long taskId) {
        String reportFilePath = getHtmlReportPath(taskId);
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
     * 按格式获取报告资源（D4-08，7/8：支持 OSS 回源下载）
     * <p>
     * 报告获取优先级：本地文件 → OSS 下载 → 抛出异常。
     * OSS 路由对 Controller 层完全透明 — 调用方无需感知存储后端。
     * </p>
     *
     * @param taskId 扫描任务 ID
     * @param format 报告格式（html / excel / pdf）
     * @return 报告文件的 Resource 对象
     */
    public Resource getReportResource(Long taskId, String format) {
        String filePath;
        String fileName;
        switch (format.toLowerCase()) {
            case "html":
                filePath = getHtmlReportPath(taskId);
                fileName = "dependency-check-report.html";
                break;
            case "excel":
                filePath = getExcelReportPath(taskId);
                fileName = "dependency-check-report-" + taskId + ".xlsx";
                break;
            case "pdf":
                filePath = getPdfReportPath(taskId);
                fileName = "dependency-check-report-" + taskId + ".pdf";
                break;
            default:
                throw new BusinessException("不支持的报告格式: " + format);
        }

        // 1. 本地文件存在 → 直接返回（快速路径）
        //    7/16：跳过 0 字节残留文件（生成中途失败会留下空文件），走重新生成
        Path localPath = Paths.get(filePath);
        try {
            if (Files.exists(localPath) && Files.size(localPath) > 0) {
                return new FileSystemResource(localPath.toFile());
            }
        } catch (IOException e) {
            log.warn("检查报告文件大小失败，尝试重新获取: {}", filePath, e);
        }

        // 2. 本地不存在 + OSS 可用 → 从 OSS 下载到本地（回源）
        if (ossService != null && ossService.isAvailable()) {
            String objectKey = ossService.buildReportKey(taskId, fileName);
            try {
                InputStream is = ossService.download(objectKey);
                if (is != null) {
                    try (is) {
                        Files.createDirectories(localPath.getParent());
                        Files.copy(is, localPath, StandardCopyOption.REPLACE_EXISTING);
                        log.info("从 OSS 回源报告到本地: taskId={}, format={}, key={}",
                                taskId, format, objectKey);
                        return new FileSystemResource(localPath.toFile());
                    }
                }
            } catch (Exception e) {
                log.warn("从 OSS 下载报告失败（回退到异常处理）: taskId={}, format={}", taskId, format, e);
            }
        }

        // 3. Excel / PDF 为衍生格式 → 从扫描结果按需生成（7/16 修复：
        //    此前 generateExcelReport/generatePdfReport 无调用方，两种格式永远 404）
        if ("excel".equalsIgnoreCase(format)) {
            generateExcelReport(taskId);
            return new FileSystemResource(localPath.toFile());
        }
        if ("pdf".equalsIgnoreCase(format)) {
            generatePdfReport(taskId);
            return new FileSystemResource(localPath.toFile());
        }

        // 4. 本地和 OSS 都没有 → 抛出异常
        throw new BusinessException("报告文件不存在: taskId=" + taskId);
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
            // 7/16：Excel / PDF 下载时按需生成，扫描完成即视为可用
            formats.add("excel");
            formats.add("pdf");
        } else {
            if (Files.exists(Paths.get(getExcelReportPath(taskId)))) {
                formats.add("excel");
            }
            if (Files.exists(Paths.get(getPdfReportPath(taskId)))) {
                formats.add("pdf");
            }
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

            // D4-07（7/8）：Excel 报告写入本地后，异步上传 OSS
            uploadReportToOss(taskId, excelPath, "dependency-check-report-" + taskId + ".xlsx");

            return excelPath;

        } catch (Exception e) {
            log.error("Excel 报告生成失败: taskId={}", taskId, e);
            // C1 修复：脱敏处理，不暴露内部异常消息（可能包含文件路径）
            throw new BusinessException("Excel 报告生成失败，请查看系统日志");
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
            // 2. 读取 HTML 内容并规范化为 XHTML（7/16 修复：DC 生成的报告是 HTML5，
            //    含未转义 & 等实体，Flying Saucer 的 XML 解析器直接解析会抛
            //    SAXParseException；用 jsoup（DC core 传递依赖）转为合法 XHTML）
            String htmlContent = Files.readString(Paths.get(htmlPath));
            org.jsoup.nodes.Document jsoupDoc = org.jsoup.Jsoup.parse(htmlContent);
            // script 为数据节点，XML 输出不转义其中的 &&，且 PDF 渲染不执行 JS，直接移除
            jsoupDoc.select("script").remove();
            jsoupDoc.outputSettings()
                    .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                    .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
                    .charset("UTF-8");
            String xhtmlContent = jsoupDoc.html();

            // 3. 创建 PDF 渲染器
            ITextRenderer renderer = new ITextRenderer();

            // 4. 设置 XHTML 内容
            renderer.setDocumentFromString(xhtmlContent);

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

            // D4-07（7/8）：PDF 报告写入本地后，异步上传 OSS
            uploadReportToOss(taskId, pdfPath, "dependency-check-report-" + taskId + ".pdf");

            return pdfPath;

        } catch (Exception e) {
            log.error("PDF 报告生成失败: taskId={}", taskId, e);
            // C1 修复：脱敏处理，不暴露内部异常消息（可能包含文件路径）
            throw new BusinessException("PDF 报告生成失败，请查看系统日志");
        }
    }

    /**
     * 获取 PDF 报告文件路径
     */
    private String getPdfReportPath(Long taskId) {
        return reportDir + File.separator + taskId + File.separator
                + "dependency-check-report-" + taskId + ".pdf";
    }

    // ==================== OSS 集成方法（D4-07/D4-08，7/8） ====================

    /**
     * 上传报告文件到 OSS（D4-07，7/8）
     * <p>
     * OSS 未启用或上传失败时静默返回，不抛出异常。
     * 本地文件作为降级兜底始终可用，OSS 上传失败不影响报告生成流程。
     * </p>
     *
     * @param taskId   扫描任务 ID
     * @param filePath 本地报告文件路径
     * @param fileName 报告文件名（用于构建 OSS Object Key）
     */
    private void uploadReportToOss(Long taskId, String filePath, String fileName) {
        if (ossService == null || !ossService.isAvailable()) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(filePath)) {
            String key = ossService.buildReportKey(taskId, fileName);
            boolean uploaded = ossService.upload(fis, key);
            if (uploaded) {
                log.info("报告已上传至 OSS: taskId={}, fileName={}, key={}", taskId, fileName, key);
            }
        } catch (Exception e) {
            log.warn("OSS 上传报告失败（不影响本地文件，本地降级仍可用）: taskId={}, fileName={}",
                    taskId, fileName, e);
        }
    }

    /**
     * 获取 HTML 报告文件路径（不检查存在性）
     * <p>
     * D4-08（7/8）：从原 {@link #getReportPath(Long)} 中提取纯路径构建逻辑，
     * 供 OSS 下载 fallback 使用。原方法保留向后兼容。
     * </p>
     */
    private String getHtmlReportPath(Long taskId) {
        return reportDir + File.separator + taskId + File.separator + "dependency-check-report.html";
    }
}
