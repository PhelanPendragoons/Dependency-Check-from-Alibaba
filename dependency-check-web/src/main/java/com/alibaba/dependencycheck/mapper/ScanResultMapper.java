package com.alibaba.dependencycheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alibaba.dependencycheck.model.entity.ScanResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 扫描结果 Mapper 接口
 */
@Mapper
public interface ScanResultMapper extends BaseMapper<ScanResult> {
}
