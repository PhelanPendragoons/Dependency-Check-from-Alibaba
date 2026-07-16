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
}


