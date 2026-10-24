package com.viomanager.person.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.auth.service.AccountService;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MemberErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.dict.service.IDictService;
import com.viomanager.dict.vo.DictItemVO;
import com.viomanager.file.entity.FileObject;
import com.viomanager.file.service.IFileService;
import com.viomanager.person.dto.PersonAccountDTO;
import com.viomanager.person.dto.PersonCreateDTO;
import com.viomanager.person.dto.PersonUpdateDTO;
import com.viomanager.person.entity.MemberSkill;
import com.viomanager.person.entity.Person;
import com.viomanager.person.mapper.MemberSkillMapper;
import com.viomanager.person.mapper.PersonMapper;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 人员档案写侧动作支撑：查重、技能整组替换、附件回填、删除级联清理、M13 账号对接。
 * 编排与权限判定在 PersonServiceImpl（业务主流程编号注释），本类只做可复用的原子动作。
 */
@Component
public class PersonCommandSupport {

    /** M08/M09 允许绑定的图片扩展名（证件照与电子签名统一口径） */
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png");

    /** M13 新账号角色：人员档案场景一律 STUDENT（教师档案也是学生权限起步，需要再由超管提权） */
    private static final String DEFAULT_ACCOUNT_ROLE = "STUDENT";

    @Resource
    private PersonMapper personMapper;

    @Resource
    private MemberSkillMapper memberSkillMapper;

    @Resource
    private PersonSensitiveSupport sensitiveSupport;

    @Resource
    private PersonTeamSupport personTeamSupport;

    @Resource
    private AccountService accountService;

    @Resource
    private IFileService fileService;

    @Resource
    private IDictService dictService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** M03/M04 学号查重（3001）；excludeId 用于编辑时排除自己 */
    public void assertStudentNoFree(String studentNo, Long excludeId) {
        if (StrUtil.isBlank(studentNo)) {
            return;
        }
        QueryWrapper<Person> wrapper = new QueryWrapper<Person>().eq("student_no", studentNo.trim());
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        if (personMapper.selectCount(wrapper) > 0) {
            throw new BizException(MemberErrorCode.STUDENT_NO_EXISTS);
        }
    }

    /** 学工号查重（登录名默认值来源，重复会让 M13 绑定失败，提前在建档时报） */
    public void assertJobNoFree(String jobNo, Long excludeId) {
        if (StrUtil.isBlank(jobNo)) {
            return;
        }
        QueryWrapper<Person> wrapper = new QueryWrapper<Person>().eq("job_no", jobNo.trim());
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        if (personMapper.selectCount(wrapper) > 0) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "学工号已存在");
        }
    }

    /** M03 组装新档案并入库（身份证加密、默认在册/无账号），返回带 id 的实体 */
    public Person insertPerson(PersonCreateDTO dto) {
        Person person = new Person()
                .setName(dto.getName().trim())
                .setStudentNo(StrUtil.trimToNull(dto.getStudentNo()))
                .setJobNo(StrUtil.trimToNull(dto.getJobNo()))
                .setPhone(StrUtil.trimToNull(dto.getPhone()))
                .setCollege(StrUtil.trimToNull(dto.getCollege()))
                .setMajor(StrUtil.trimToNull(dto.getMajor()))
                .setClassName(StrUtil.trimToNull(dto.getClassName()))
                .setGender(StrUtil.blankToDefault(dto.getGender(), "UNKNOWN"))
                .setNation(StrUtil.trimToNull(dto.getNation()))
                .setEmail(StrUtil.trimToNull(dto.getEmail()))
                .setOrgId(dto.getOrgId())
                .setMemberType(StrUtil.blankToDefault(dto.getMemberType(), "STUDENT"))
                .setRemark(StrUtil.trimToNull(dto.getRemark()))
                .setStatus(1)
                .setHasAccount(0);
        if (StrUtil.isNotBlank(dto.getIdCard())) {
            person.setIdCard(sensitiveSupport.encryptIdCard(dto.getIdCard()))
                    .setIdCardLast4(sensitiveSupport.last4OfIdCard(dto.getIdCard()));
        }
        personMapper.insert(person);
        return person;
    }

    /** M04 部分更新：null 不改；身份证传了重新加密并刷新尾号；学号不在此列（不可修改） */
    public void applyUpdate(Person person, PersonUpdateDTO dto) {
        if (dto.getName() != null) {
            person.setName(dto.getName().trim());
        }
        if (dto.getJobNo() != null) {
            person.setJobNo(StrUtil.trimToNull(dto.getJobNo()));
        }
        if (dto.getPhone() != null) {
            person.setPhone(StrUtil.trimToNull(dto.getPhone()));
        }
        if (dto.getCollege() != null) {
            person.setCollege(dto.getCollege());
        }
        if (dto.getMajor() != null) {
            person.setMajor(dto.getMajor());
        }
        if (dto.getClassName() != null) {
            person.setClassName(dto.getClassName());
        }
        if (dto.getGender() != null) {
            person.setGender(dto.getGender());
        }
        if (dto.getNation() != null) {
            person.setNation(dto.getNation());
        }
        if (dto.getEmail() != null) {
            person.setEmail(StrUtil.trimToNull(dto.getEmail()));
        }
        if (dto.getOrgId() != null) {
            person.setOrgId(dto.getOrgId());
        }
        if (dto.getMemberType() != null) {
            person.setMemberType(dto.getMemberType());
        }
        if (dto.getStatus() != null) {
            person.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            person.setRemark(dto.getRemark());
        }
        if (StrUtil.isNotBlank(dto.getIdCard())) {
            person.setIdCard(sensitiveSupport.encryptIdCard(dto.getIdCard()))
                    .setIdCardLast4(sensitiveSupport.last4OfIdCard(dto.getIdCard()));
        }
        personMapper.updateById(person);
    }

    /**
     * 技能标签整组替换（M03/M04/M12 共用）：显示名校验（必须是 SKILL 字典可用项的 itemLabel）
     * →物理删旧→插入（skill_value 与 skill_label 同存显示名）。
     */
    public void saveSkills(Long memberId, List<String> skills) {
        //0.去重保序：重复项会在 uk_member_skill(member_id, skill_value, deleted) 上撞 1062 变 500
        if (skills != null) {
            skills = skills.stream().distinct().toList();
        }
        //1.校验提交项全部是字典可用项的显示名（PRESET 或已审核通过的用户补充）——
        //  前端下拉/提交/回显/筛选全链路 label 语系（2026-10-24 拍板，见修复说明）
        Map<String, String> labelMap = availableSkillLabels();
        if (skills != null) {
            for (String value : skills) {
                if (!labelMap.containsKey(value)) {
                    throw new BizException(CommonErrorCode.PARAM_INVALID, "技能标签不在字典中：" + value);
                }
            }
        }
        //2.物理删旧（唯一键 uk_member_skill 决定了不能走逻辑删除，见 mapper 注释）
        memberSkillMapper.deleteByMemberId(memberId);
        //3.逐条插入（skill_value 与 skill_label 同为显示名——M01/M02 回显与 skill 筛选都走 skill_value）
        if (skills != null) {
            for (String value : skills) {
                MemberSkill skill = new MemberSkill()
                        .setMemberId(memberId)
                        .setSkillValue(value)
                        .setSkillLabel(labelMap.get(value));
                memberSkillMapper.insert(skill);
            }
        }
    }

    /** M08/M09 附件回填：文件须存在且为当前登录人上传、扩展名 jpg/jpeg/png、已过病毒扫描 */
    public void bindFile(Person person, Long fileId, boolean photo) {
        //1.归属校验（不存在与不是本人上传统一 1001，不给试探空间）
        FileObject file = fileService.loadOwnedFile(fileId, UserHolder.getUser().getUserId());
        //2.扩展名白名单
        String ext = file.getFileExt() == null ? "" : file.getFileExt().toLowerCase();
        if (!IMAGE_EXTS.contains(ext)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "仅支持 jpg/jpeg/png 格式图片");
        }
        //3.病毒闸门：只有 CLEAN/SKIPPED 能挂到档案上（INFECTED 的文件在 F01 上传时已抛 1010，这里是双保险）
        if (!"CLEAN".equals(file.getScanStatus()) && !"SKIPPED".equals(file.getScanStatus())) {
            throw new BizException(CommonErrorCode.FILE_INFECTED);
        }
        //4.回填档案（覆盖式单图：后传的直接顶掉前传的）
        personMapper.update(null, new UpdateWrapper<Person>()
                .set(photo ? "photo_file_id" : "signature_file_id", fileId)
                .eq("id", person.getId()));
    }

    /** M05 删除级联：逻辑删档案→物理删技能→清队伍关系→禁用其登录账号（孤儿账号口子，需 lzh 拍板保留与否） */
    public void deletePersonCascade(Person person) {
        //1.逻辑删档案（@TableLogic → deleted=1）；uk_member_student_no/job_no 含 deleted 列，
        //  同学号"删过一次又重建再删"时会出现第二个 (X,1) 撞唯一键——转成可理解的业务错误而非 500
        try {
            personMapper.deleteById(person.getId());
        } catch (DuplicateKeyException e) {
            throw new BizException(CommonErrorCode.PARAM_INVALID,
                    "该学号/学工号已被历史删除档案占用，无法删除，请联系超级管理员清理数据");
        }
        //2.物理删技能标签（从属数据，同"整组替换"的物理删理由）
        memberSkillMapper.deleteByMemberId(person.getId());
        //3.清队伍成员关系（能走到这里说明不是活跃队长）
        personTeamSupport.removeActiveRelations(person.getId());
        //4.禁用登录账号
        jdbcTemplate.update("UPDATE sys_user SET status = 0 WHERE member_id = ? AND deleted = 0",
                person.getId());
    }

    /**
     * M13 绑定账号：登录名默认链 = 显式传入 → 学工号 → 学号；初始密码与首登改密由
     * 认证模块 AccountService 兜住；成功后回写 member.has_account=1。
     */
    public void bindAccountFor(Person person, String loginName) {
        //1.默认链
        String resolved = StrUtil.trimToNull(loginName);
        if (resolved == null) {
            resolved = StrUtil.trimToNull(person.getJobNo());
        }
        if (resolved == null) {
            resolved = StrUtil.trimToNull(person.getStudentNo());
        }
        if (resolved == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "请填写登录名（学工号/学号均未填）");
        }
        //2.建账号（登录名重复 2003 在账号服务内判定）
        accountService.bindAccount(person.getId(), resolved, DEFAULT_ACCOUNT_ROLE,
                person.getOrgId(), person.getName());
        //3.回写档案标记
        personMapper.update(null, new UpdateWrapper<Person>()
                .set("has_account", 1).eq("id", person.getId()));
    }

    /** M13 修改账号：改登录名（同名跳过）/ 重置密码（回初始密码并失效旧 token） */
    public void changeAccountFor(Person person, PersonAccountDTO dto) {
        //1.找到绑定的账号（id 与当前登录名一次查回来）
        Long userId;
        String currentLogin;
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT id, login_name FROM sys_user WHERE member_id = ? AND deleted = 0", person.getId());
            userId = (Long) row.get("id");
            currentLogin = (String) row.get("login_name");
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "该人员未绑定登录账号");
        }
        //2.改登录名（与当前相同则跳过——账号服务按"已存在"判定会误伤自己）
        boolean hasChange = false;
        String newLogin = StrUtil.trimToNull(dto.getLoginName());
        if (newLogin != null && !newLogin.equals(currentLogin)) {
            accountService.changeLoginName(userId, newLogin);
            hasChange = true;
        }
        //3.重置密码
        if (Boolean.TRUE.equals(dto.getResetPassword())) {
            accountService.resetPassword(userId);
            hasChange = true;
        }
        //4.两个动作都没触发说明调用方传了空参
        if (!hasChange) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "没有可执行的账号变更");
        }
    }

    /** SKILL 字典可用项的显示名集合（键=值=label；PRESET 或审核通过）——校验键用 itemLabel 而非
     *  itemValue：前端全链路提交显示名，旧口径按 value 收键在"值≠名"条目上必炸（2026-10-24 修复） */
    private Map<String, String> availableSkillLabels() {
        List<DictItemVO> items = dictService.listItems("SKILL").getData();
        if (items == null) {
            return Map.of();
        }
        return items.stream()
                .filter(item -> "PRESET".equals(item.getSource())
                        || "APPROVED".equals(item.getAuditStatus()))
                .collect(Collectors.toMap(DictItemVO::getItemLabel, DictItemVO::getItemLabel,
                        (first, second) -> first));
    }
}
