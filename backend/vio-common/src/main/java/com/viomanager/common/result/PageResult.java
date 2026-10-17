package com.viomanager.common.result;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 分页响应容器（规范 §2.3）：data 固定形态 { list, total, page, size }。
 * 请求侧默认 size=20、上限 100，由各查询接口自行校验。
 * ⚠️ total/page/size 刻意用 int 而非 long：全局 Jackson 把 Long 序列化为字符串
 * （雪花 ID 精度保护，规范 §2.1），long 字段会让前端拿到 "15" 字符串，与 TS 的
 * number 契约不符（审查 L10，lzh 2026-10-16 拍板改后端出真数字）。
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> list;

    /** 总条数 */
    private int total;

    /** 当前页码（从 1 开始） */
    private int page;

    /** 每页条数 */
    private int size;

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        // 收窄安全：计数与页码远不到 int 上限；调用方（MyBatis-Plus Page）出参是 long
        result.setTotal((int) total);
        result.setPage((int) page);
        result.setSize((int) size);
        return result;
    }
}
