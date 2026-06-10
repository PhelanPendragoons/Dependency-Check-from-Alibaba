package com.alibaba.dependencycheck.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 扫描任务实体类
 * <p>
 * 对应数据库表 scan_task，记录每次扫描任务的执行状态和结果摘要。
 * 一个项目可以对应多次扫描任务。
 * </p>
 */
@Data
@TableName("scan_task")
public class ScanTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联项目ID */
    private Long projectId;

    /** 任务状态（PENDING / RUNNING / COMPLETED / FAILED / CANCELLED） */
    private String status;

    /** 扫描进度（0-100） */
    private Integer progress;

    /** 发现的依赖总数 */
    private Integer totalDependencies;

    /** 有漏洞的依赖数 */
    private Integer vulnerableDependencies;

    /** 错误信息（任务失败时记录） */
    private String errorMessage;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
