package com.alibaba.dependencycheck.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 扫描结果实体类
 * <p>
 * 对应数据库表 scan_result，存储每次扫描发现的依赖漏洞和许可证信息。
 * 一个扫描任务对应多条扫描结果。
 * </p>
 */
@Data
@TableName("scan_result")
public class ScanResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务ID */
    private Long taskId;

    /** 依赖名称 */
    private String dependencyName;

    /** 依赖版本 */
    private String dependencyVersion;

    /** 文件路径 */
    private String filePath;

    /** CVE编号（如 CVE-2021-44228） */
    private String cveId;

    /** CVSS评分（0.0 - 10.0） */
    private BigDecimal cvssScore;

    /** 严重等级（LOW / MEDIUM / HIGH / CRITICAL） */
    private String severity;

    /** 漏洞描述 */
    private String description;

    /** 许可证名称（如 Apache 2.0） */
    private String licenseName;

    /** 许可证URL */
    private String licenseUrl;

    /** 是否有漏洞 */
    private Boolean isVulnerable;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
