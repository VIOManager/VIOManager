package com.viomanager.kb.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库详情（KB02）：列表卡片字段 + 当前登录人是否已收藏（强一致个性化字段，永不缓存）。
 * 正文/封面等字段已随 2026-10-14 拍板简化移除，待正式需求加回时再扩展。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KbDocDetailVO extends KbDocVO {

    /** 当前登录用户是否已收藏 */
    private Boolean favorited;
}
