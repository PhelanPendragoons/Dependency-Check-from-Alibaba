package com.alibaba.dependencycheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alibaba.dependencycheck.model.entity.Project;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目 Mapper 接口
 * <p>
 * 使用 MyBatis-Plus 提供的基础 CRUD 功能，无需编写 SQL。
 * </p>
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
