package com.alibaba.dependencycheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alibaba.dependencycheck.model.entity.ScanTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 扫描任务 Mapper 接口
 */
@Mapper
public interface ScanTaskMapper extends BaseMapper<ScanTask> {
}
