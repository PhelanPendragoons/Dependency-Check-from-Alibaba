package com.alibaba.dependencycheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alibaba.dependencycheck.model.entity.ScanTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 扫描任务 Mapper 接口
 */
@Mapper
public interface ScanTaskMapper extends BaseMapper<ScanTask> {

    /**
     * 按项目 ID 查询扫描任务列表（按创建时间降序）
     *
     * @param projectId 项目 ID
     * @return 扫描任务列表
     */
    @Select("SELECT * FROM scan_task WHERE project_id = #{projectId} ORDER BY created_at DESC")
    List<ScanTask> findByProjectId(Long projectId);
}


