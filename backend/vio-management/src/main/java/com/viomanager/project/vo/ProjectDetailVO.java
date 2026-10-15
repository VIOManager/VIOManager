package com.viomanager.project.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目详情（PR03）：卡片字段 + 描述与备注。
 * 可见性八条判定通过后才能到达；ONCE 授权的消耗在 detail 入口触发（M6 里程碑实现）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectDetailVO extends ProjectVO {

    /** 项目描述 */
    private String description;

    /** 备注 */
    private String remark;
}
