package com.viomanager.dict.service;

import com.viomanager.common.result.Result;
import com.viomanager.dict.vo.DictItemVO;
import java.util.List;

/**
 * 字典服务：前端枚举下拉的统一数据源（骨架接口清单 D01）。
 */
public interface IDictService {

    /**
     * 按字典编码读字典项列表（PRESET + 用户补充，带审核状态）。
     * 登录即可调；编码不存在返回空数组（下拉框场景宁空不报错）。
     */
    Result<List<DictItemVO>> listItems(String code);

    /**
     * D02 用户补充技能字典项：source=USER、audit_status=PENDING，值重复拒绝。
     * 登录即可提交（学生补充自己会的技能），审核通过才进下拉。
     */
    Long createSkillItem(String dictCode, com.viomanager.dict.dto.DictItemCreateDTO dto);

    /**
     * D03 管理员审核字典项：PENDING 审核通过（APPROVED 入库）/ 驳回（REJECTED）。
     * 仅 PENDING 可审，条件更新防重复审核。
     */
    void auditItem(Long id, com.viomanager.dict.dto.DictItemAuditDTO dto);
}
