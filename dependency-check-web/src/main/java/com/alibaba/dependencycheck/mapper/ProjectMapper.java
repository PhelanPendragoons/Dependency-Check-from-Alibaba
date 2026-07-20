package com.alibaba.dependencycheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alibaba.dependencycheck.model.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 项目 Mapper 接口
 * <p>
 * 使用 MyBatis-Plus 提供的基础 CRUD 功能，无需编写 SQL。
 * </p>
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 按项目名称查询
     *
     * @param name 项目名称
     * @return 项目实体，不存在时返回 null
     */
    @Select("SELECT * FROM project WHERE name = #{name} AND deleted = 0")
    Project findByName(String name);

    /**
     * 物理删除指定名称的已逻辑删除项目（7/20 修复逻辑删除 + 唯一索引冲突）
     * <p>
     * 逻辑删除将 {@code deleted} 设为 1，但唯一索引 {@code CONSTRAINT_INDEX_1 ON project(name)}
     * 仍包含这些行，导致同名新项目插入时抛 {@code DuplicateKeyException}。
     * 此方法在创建项目前先清理已逻辑删除的同名记录。
     * </p>
     *
     * @param name 项目名称（仅删除 deleted=1 的行）
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM project WHERE name = #{name} AND deleted = 1")
    int physicalDeleteLogicDeletedByName(String name);
}


