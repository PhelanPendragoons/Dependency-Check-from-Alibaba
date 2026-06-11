package com.alibaba.dependencycheck.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}


