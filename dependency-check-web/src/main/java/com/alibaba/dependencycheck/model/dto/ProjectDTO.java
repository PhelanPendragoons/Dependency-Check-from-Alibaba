package com.alibaba.dependencycheck.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目数据传输对象
 * <p>
 * 用于 API 接口返回项目信息，不包含敏感字段。
 * </p>
 */
@Data
public class ProjectDTO {

    private Long id;
    private String name;
    private String description;
    private String fileType;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}


