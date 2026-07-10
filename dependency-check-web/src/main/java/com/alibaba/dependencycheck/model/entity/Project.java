package com.alibaba.dependencycheck.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目实体类
 * <p>
 * 对应数据库表 project，存储用户上传的项目信息。
 * 每个项目可以关联多个扫描任务。
 * </p>
 */
@Data
@TableName("project")
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    private String name;

    /** 项目描述 */
    private String description;

    /** 项目文件存储路径（上传解压后的目录路径） */
    private String filePath;

    /** 文件类型（ZIP / JAR / DIRECTORY） */
    private String fileType;

    /** 状态（UPLOADED / SCANNING / COMPLETED / FAILED） */
    private String status;

    /** 逻辑删除标志（0=未删除，1=已删除） */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
