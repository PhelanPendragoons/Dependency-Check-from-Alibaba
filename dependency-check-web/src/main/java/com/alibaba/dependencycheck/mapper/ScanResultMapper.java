package com.alibaba.dependencycheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alibaba.dependencycheck.model.entity.ScanResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 扫描结果 Mapper 接口
 */
@Mapper
public interface ScanResultMapper extends BaseMapper<ScanResult> {

    /**
     * 按任务 ID 查询扫描结果列表
     *
     * @param taskId 任务 ID
     * @return 扫描结果列表
     */
    @Select("SELECT * FROM scan_result WHERE task_id = #{taskId}")
    List<ScanResult> findByTaskId(Long taskId);
}


