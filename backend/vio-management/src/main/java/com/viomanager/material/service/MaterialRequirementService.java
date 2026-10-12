package com.viomanager.material.service;

import com.viomanager.common.result.Result;
import com.viomanager.material.dto.MaterialRequirementCreateDTO;
import com.viomanager.material.dto.MaterialRequirementUpdateDTO;
import com.viomanager.material.vo.MaterialRequirementVO;
import java.util.List;

/**
 * 材料要求服务：任务下要求列表 + MR01–MR03（添加 / 编辑 / 删除），管理端配置接口，
 * 口径以 C 线接口清单 §2 为准。
 */
public interface MaterialRequirementService {

    /** 任务下要求列表：查该任务所有未删要求，sort_no 升序 / id 升序（展示顺序稳定） */
    Result<List<MaterialRequirementVO>> listByTask(Long taskId);

    /** 添加要求（MR01）：contestId 从所属任务带出；systemGenerated 恒 0；入参校验失败抛 1001 */
    Result<MaterialRequirementVO> create(Long taskId, MaterialRequirementCreateDTO dto);

    /** 编辑要求（MR02）：系统生成项 403 拒绝；taskId / contestId 不参与修改 */
    void update(Long id, MaterialRequirementUpdateDTO dto);

    /** 删除要求（MR03）：已有提交记录抛 5005；系统生成项 403 拒绝；逻辑删除 */
    void delete(Long id);
}
