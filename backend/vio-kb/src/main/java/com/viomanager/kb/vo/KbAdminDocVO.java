package com.viomanager.kb.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * KB17 管理视角列表卡片：公共卡片之上多一个 status 徽标字段。
 * 状态是管理概念——浏览侧只见 PUBLISHED，公共 KbDocVO 刻意不含此字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KbAdminDocVO extends KbDocVO {

    /** 状态：DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下架（管理端徽标展示） */
    private String status;
}
