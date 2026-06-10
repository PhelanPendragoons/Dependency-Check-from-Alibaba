package com.alibaba.dependencycheck.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 扫描任务数据传输对象
 */
@Data
public class ScanTaskDTO {

    private Long id;
    private Long projectId;
    private String projectName;
    private String status;
    private Integer progress;
    private Integer totalDependencies;
    private Integer vulnerableDependencies;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
