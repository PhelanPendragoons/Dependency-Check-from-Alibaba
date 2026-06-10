package com.alibaba.dependencycheck.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 分页返回结果
 *
 * @param <T> 列表数据类型
 */
@Data
public class PageResult<T> {

    /** 数据列表 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private int page;

    /** 每页大小 */
    private int pageSize;

    /** 总页数 */
    private long pages;

    public PageResult(List<T> records, long total, int page, int pageSize) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.pages = (total + pageSize - 1) / pageSize;
    }
}
