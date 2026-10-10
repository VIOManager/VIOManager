package com.viomanager.common.result;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 分页响应容器（规范 §2.3）：data 固定形态 { list, total, page, size }。
 * 请求侧默认 size=20、上限 100，由各查询接口自行校验。
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> list;

    /** 总条数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private long page;

    /** 每页条数 */
    private long size;

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        return result;
    }
}
