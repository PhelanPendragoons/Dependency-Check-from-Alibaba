package com.alibaba.dependencycheck.model.dto;

import lombok.Data;

/**
 * 项目更新请求 DTO
 * <p>
 * P1#103：仅允许更新 name 和 description，严禁触碰其他字段。
 * </p>
 */
@Data
public class UpdateProjectRequest {

    /** 项目名称（可选，不传则保留原名） */
    private String name;

    /** 项目描述（可选，不传则保留原描述） */
    private String description;
}
