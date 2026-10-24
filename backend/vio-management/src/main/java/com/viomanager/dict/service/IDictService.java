package com.viomanager.dict.service;

import com.viomanager.common.result.Result;
import com.viomanager.dict.dto.DictItemAuditAdminDTO;
import com.viomanager.dict.dto.DictItemAuditDTO;
import com.viomanager.dict.dto.DictItemCreateDTO;
import com.viomanager.dict.dto.DictItemStatusDTO;
import com.viomanager.dict.dto.DictItemUpdateDTO;
import com.viomanager.dict.dto.DictTypeCreateDTO;
import com.viomanager.dict.dto.DictTypeUpdateDTO;
import com.viomanager.dict.vo.DictItemAdminVO;
import com.viomanager.dict.vo.DictItemVO;
import com.viomanager.dict.vo.DictTypeVO;
import java.util.List;

/**
 * 字典服务：D01-D03（A 线读/补充/审核）+ SY01-10（系统配置字典管理，超管）。
 */
public interface IDictService {

    /**
     * D01 按字典编码读字典项列表（PRESET + 用户补充，带审核状态）。
     * 登录即可调；编码不存在返回空数组（下拉框场景宁空不报错）。
     */
    Result<List<DictItemVO>> listItems(String code);

    /**
     * D02 用户补充 / SY06 超管预置（同一口按角色分流，见 DictItemAdminSupport）。
     * 超管：source=PRESET、直接 APPROVED；其他角色：仅 SKILL、USER/PENDING 待审核。
     */
    Long createItem(String dictCode, DictItemCreateDTO dto);

    /**
     * D03 管理员审核字典项（POST 形态，A 线面板用，approve/pass 都收）：
     * PENDING 审核通过（APPROVED 入库）/ 驳回（REJECTED）。条件更新防重复审核。
     */
    void auditItem(Long id, DictItemAuditDTO dto);

    /** SY01 类型列表：dictCode dictName remark 条目数 待审核数 preset */
    Result<List<DictTypeVO>> listTypes();

    /** SY02 新建类型：编码重复 → 1001 */
    Long createType(DictTypeCreateDTO dto);

    /** SY03 编辑类型名称/说明（dictCode 不可改） */
    void updateType(Long id, DictTypeUpdateDTO dto);

    /** SY04 删除类型：预设或仍有条目 → 1006 */
    void deleteType(Long id);

    /** SY05 管理视角条目列表（含停用/待审核项）。筛选：source status auditStatus keyword */
    Result<List<DictItemAdminVO>> adminItems(String code, String source, String status,
                                             String auditStatus, String keyword);

    /** SY07 编辑条目显示名/排序 */
    void updateItem(Long id, DictItemUpdateDTO dto);

    /** SY08 逻辑删除条目（停用优先于删除） */
    void deleteItem(Long id);

    /** SY09 启用/停用条目 */
    void toggleItem(Long id, DictItemStatusDTO dto);

    /** SY10 审核用户补充词条（PUT 形态，系统配置页用） */
    void auditItemAdmin(Long id, DictItemAuditAdminDTO dto);
}
