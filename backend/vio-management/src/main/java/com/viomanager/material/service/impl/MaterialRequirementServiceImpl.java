package com.viomanager.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MaterialErrorCode;
import com.viomanager.common.enums.MaterialTypeEnum;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.material.dto.MaterialRequirementCreateDTO;
import com.viomanager.material.dto.MaterialRequirementUpdateDTO;
import com.viomanager.material.entity.MaterialRequirement;
import com.viomanager.material.entity.MaterialSubmission;
import com.viomanager.material.entity.MaterialTask;
import com.viomanager.material.mapper.MaterialRequirementMapper;
import com.viomanager.material.mapper.MaterialSubmissionMapper;
import com.viomanager.material.mapper.MaterialTaskMapper;
import com.viomanager.material.service.MaterialRequirementService;
import com.viomanager.material.vo.MaterialRequirementVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 材料要求服务实现（任务下要求列表 + MR01–MR03），口径以 C 线接口清单 §2 为准。
 *
 * 入参校验为 service 层手写（PARAM_INVALID + 接口清单指定文案），不走 Bean Validation——
 * 清单对每条校验都定死了报错文案，注解方式凑不出这些口径。
 *
 * ⚠️ systemGenerated 守卫：§5 联动产生的佐证要求（systemGenerated=1）管理端不可改不可删
 * （MR02 / MR03 拦截，见 MaterialRequirement 实体注释）。
 * allowedFormats 形态转换：入参 List → 落库逗号分隔串；出参逗号串 → List（空串归空 List）。
 */
@Service
public class MaterialRequirementServiceImpl implements MaterialRequirementService {

    /** maxSizeMb 合法下界；入参为空表示用全局配置 vio.file.max-size-mb */
    private static final int MAX_SIZE_MB_MIN = 1;

    /** maxSizeMb 合法上界（与文件服务单文件硬上限对齐） */
    private static final int MAX_SIZE_MB_MAX = 200;

    @Resource
    private MaterialTaskMapper taskMapper;

    @Resource
    private MaterialRequirementMapper requirementMapper;

    @Resource
    private MaterialSubmissionMapper submissionMapper;

    @Override
    public Result<List<MaterialRequirementVO>> listByTask(Long taskId) {
        //1.任务必须存在（selectById 经 @TableLogic 自动滤已删）
        if (taskMapper.selectById(taskId) == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料任务不存在");
        }
        //2.查该任务下全部未删要求：sort_no 升序、同序号再按 id 升序，保证展示顺序稳定
        List<MaterialRequirement> requirements = requirementMapper.selectList(
                new QueryWrapper<MaterialRequirement>()
                        .eq("task_id", taskId)
                        .orderByAsc("sort_no")
                        .orderByAsc("id"));
        List<MaterialRequirementVO> voList = new ArrayList<>(requirements.size());
        for (MaterialRequirement requirement : requirements) {
            voList.add(toVO(requirement));
        }
        return Result.ok(voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<MaterialRequirementVO> create(Long taskId, MaterialRequirementCreateDTO dto) {
        //1.所属任务必须存在（contestId 冗余列要从它身上带出；@TableLogic 自动滤已删）
        MaterialTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料任务不存在");
        }
        //2.校验并归一入参
        ValidatedFields fields = validate(dto.getMaterialType(), dto.getName(),
                dto.getRequiredFlag(), dto.getAllowedFormats(), dto.getMaxSizeMb());
        //3.落库：contestId 随任务带出（冗余列必填）；systemGenerated 恒 0——
        //   系统佐证要求只由 §5 联动产生，管理端创建的一律是普通要求；
        //   审计四字段由 AuditMetaObjectHandler 自动填
        MaterialRequirement requirement = new MaterialRequirement();
        requirement.setTaskId(taskId);
        requirement.setContestId(task.getContestId());
        requirement.setMaterialType(fields.materialType());
        requirement.setName(fields.name());
        requirement.setRequiredFlag(fields.requiredFlag());
        requirement.setFormats(fields.formatsJoined());
        requirement.setMaxSizeMb(fields.maxSizeMb());
        requirement.setNamingRule(dto.getNamingRule());
        requirement.setSystemGenerated(0);
        requirementMapper.insert(requirement);
        return Result.ok(toVO(requirement));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MaterialRequirementUpdateDTO dto) {
        //1.要求必须存在
        MaterialRequirement requirement = requirementMapper.selectById(id);
        if (requirement == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料要求不存在");
        }
        //2.系统生成的佐证要求不可编辑（§5 联动管理，实体注释有约定）
        checkNotSystemGenerated(requirement, "系统生成的佐证要求不可编辑");
        //3.校验并归一入参（taskId / contestId 不在修改范围，实体旧值不动）
        ValidatedFields fields = validate(dto.getMaterialType(), dto.getName(),
                dto.getRequiredFlag(), dto.getAllowedFormats(), dto.getMaxSizeMb());
        //4.只更新六个业务字段；maxSizeMb / namingRule 允许置 null（恢复全局默认 / 清空）
        UpdateWrapper<MaterialRequirement> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("material_type", fields.materialType())
                .set("name", fields.name())
                .set("required_flag", fields.requiredFlag())
                .set("allowed_formats", fields.formatsJoined())
                .set("max_size_mb", fields.maxSizeMb())
                .set("naming_rule", dto.getNamingRule());
        if (requirementMapper.update(null, wrapper) == 0) {
            //并发兜底：查出后要求恰好被他人删除
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料要求不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        //1.要求必须存在
        MaterialRequirement requirement = requirementMapper.selectById(id);
        if (requirement == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料要求不存在");
        }
        //2.已有提交记录 → 5005 拒绝（selectCount 经 @TableLogic 自动带 deleted=0）
        Long submitted = submissionMapper.selectCount(
                new QueryWrapper<MaterialSubmission>().eq("requirement_id", id));
        if (submitted != null && submitted > 0) {
            throw new BizException(MaterialErrorCode.REQUIREMENT_HAS_SUBMISSION);
        }
        //3.系统生成的佐证要求不可删除
        checkNotSystemGenerated(requirement, "系统生成的佐证要求不可删除");
        //4.逻辑删除（@TableLogic 把 delete 转成 UPDATE deleted=1，不物理删）
        requirementMapper.deleteById(id);
    }

    /** 系统生成守卫（MR02 / MR03 共用）：systemGenerated=1 按给定文案 403 拒绝 */
    private void checkNotSystemGenerated(MaterialRequirement requirement, String message) {
        if (requirement.getSystemGenerated() != null && requirement.getSystemGenerated() == 1) {
            throw new BizException(CommonErrorCode.FORBIDDEN, message);
        }
    }

    /**
     * 校验并归一创建 / 编辑共用的五个字段：不合法即抛 PARAM_INVALID（文案照接口清单），
     * 合法返回可直接落库的归一值（name 已 trim，formats 已 trim / 去空 / 去重并拼好逗号串，
     * requiredFlag 已补默认）。
     */
    private ValidatedFields validate(String materialType, String name, Integer requiredFlag,
                                     List<String> allowedFormats, Integer maxSizeMb) {
        if (!MaterialTypeEnum.isValid(materialType)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "材料类型必须是 12 种预设之一");
        }
        if (name == null || name.isBlank()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "要求名称不能为空");
        }
        List<String> formats = allowedFormats == null ? List.of()
                : allowedFormats.stream().map(String::trim).filter(f -> !f.isEmpty()).distinct().toList();
        if (formats.isEmpty()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "至少允许一种文件格式");
        }
        if (maxSizeMb != null && (maxSizeMb < MAX_SIZE_MB_MIN || maxSizeMb > MAX_SIZE_MB_MAX)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "单文件大小限制须在 1–200MB 之间");
        }
        //requiredFlag：空 = 默认必传（与表 DEFAULT 1 对齐）；给了就只认 0 / 1
        int flag = requiredFlag == null ? 1 : requiredFlag;
        if (flag != 0 && flag != 1) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "requiredFlag 只能是 0（选传）或 1（必传）");
        }
        return new ValidatedFields(materialType, name.trim(), flag,
                String.join(",", formats), maxSizeMb);
    }

    /** 实体转出参：类型附中文名，allowed_formats 逗号串拆回 List */
    private MaterialRequirementVO toVO(MaterialRequirement requirement) {
        MaterialRequirementVO vo = new MaterialRequirementVO();
        vo.setId(requirement.getId());
        vo.setTaskId(requirement.getTaskId());
        vo.setContestId(requirement.getContestId());
        vo.setMaterialType(requirement.getMaterialType());
        vo.setMaterialTypeLabel(MaterialTypeEnum.labelOf(requirement.getMaterialType()));
        vo.setName(requirement.getName());
        vo.setRequiredFlag(requirement.getRequiredFlag());
        vo.setAllowedFormats(splitFormats(requirement.getFormats()));
        vo.setMaxSizeMb(requirement.getMaxSizeMb());
        vo.setNamingRule(requirement.getNamingRule());
        vo.setSystemGenerated(requirement.getSystemGenerated());
        vo.setCreatedAt(requirement.getCreatedAt());
        return vo;
    }

    /** 逗号分隔串拆 List：null / 空白 → 空 List；元素逐个 trim 并滤掉空段（防历史脏数据） */
    private List<String> splitFormats(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).map(String::trim).filter(f -> !f.isEmpty()).toList();
    }

    /** 校验通过后的归一值：formatsJoined 为落库的逗号串形态 */
    private record ValidatedFields(String materialType, String name, int requiredFlag,
                                   String formatsJoined, Integer maxSizeMb) {
    }
}
