package com.viomanager.dict.dto;

import lombok.Data;

/**
 * D03 管理员审核字典项（POST 形态，A 线字典审核面板用）。
 * approve 是后端原字段；A 线前端发的是 pass——两个名字都收，
 * 判空挪到服务层（原先 @NotNull 挡住了只发 pass 的请求）。
 */
@Data
public class DictItemAuditDTO {

    /** 审核结论：true 通过 / false 驳回 */
    private Boolean approve;

    /** A 线前端别名字段（与 approve 等效，任填一个） */
    private Boolean pass;
}
