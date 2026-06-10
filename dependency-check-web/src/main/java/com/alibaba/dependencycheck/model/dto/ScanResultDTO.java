package com.alibaba.dependencycheck.model.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 扫描结果数据传输对象
 */
@Data
public class ScanResultDTO {

    private Long id;
    private Long taskId;
    private String dependencyName;
    private String dependencyVersion;
    private String filePath;
    private String cveId;
    private BigDecimal cvssScore;
    private String severity;
    private String description;
    private String licenseName;
    private String licenseUrl;
    private Boolean isVulnerable;
}
