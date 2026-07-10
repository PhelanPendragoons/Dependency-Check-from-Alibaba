package com.alibaba.dependencycheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alibaba.dependencycheck.model.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
     * P1#103：按名称查询但排除指定 ID（用于更新时的重名检查）
     * <p>
     * 与 {@link #findByName} 不同，此方法排除自身 ID，
     * 避免更新项目名时因"同名"误报冲突。
     * </p>
     *
     * @param name      项目名称
     * @param excludeId 要排除的项目 ID（通常为当前正在更新的项目）
     * @return 项目实体，不存在时返回 null
     */
    @Select("SELECT * FROM project WHERE name = #{name} AND id != #{excludeId} AND deleted = 0")
    Project findByNameAndIdNot(@Param("name") String name, @Param("excludeId") Long excludeId);
}


