package com.viomanager.board.vo;

import lombok.Data;

/**
 * 版块列表项（B01）：替换前端硬编码的 BOARDS 常量，服务端为唯一数据源。
 */
@Data
public class BoardVO {

    /** 版块编码（GENERAL/EE/MODELING/AI） */
    private String code;

    /** 版块名 */
    private String name;

    /** 版块简介 */
    private String desc;
}
