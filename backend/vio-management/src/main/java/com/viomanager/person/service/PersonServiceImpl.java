package com.viomanager.person.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MemberErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.file.service.IFileService;
import com.viomanager.person.dto.MemberOptionVO;
import com.viomanager.person.dto.PersonAccountDTO;
import com.viomanager.person.dto.PersonBatchDeleteDTO;
import com.viomanager.person.dto.PersonCreateDTO;
import com.viomanager.person.dto.PersonFileBindDTO;
import com.viomanager.person.dto.PersonIdCardVO;
import com.viomanager.person.dto.PersonImportVO;
import com.viomanager.person.dto.PersonQueryDTO;
import com.viomanager.person.dto.PersonUpdateDTO;
import com.viomanager.person.dto.PersonVO;
import com.viomanager.person.entity.Person;
import com.viomanager.person.mapper.PersonMapper;
import com.viomanager.person.support.PersonBatchDeleteSupport;
import com.viomanager.person.support.PersonCommandSupport;
import com.viomanager.person.support.PersonExportSupport;
import com.viomanager.person.support.PersonImportSupport;
import com.viomanager.person.support.PersonQuerySupport;
import com.viomanager.person.support.PersonSensitiveSupport;
import com.viomanager.person.support.PersonTeamSupport;
import com.viomanager.person.support.PersonViewerSupport;
import com.viomanager.person.support.PersonViewerSupport.View;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人员档案服务实现（A 线契约 M01–M13 + export）：只做编排与业务规则判定（编号注释），
 * 原子动作拆在 support 包——视角判定 PersonViewerSupport、查询裁剪 PersonQuerySupport、
 * 写侧动作 PersonCommandSupport、导入 PersonImportSupport、Excel 读写与响应头 PersonExcelWriter。
 */
@Service
public class PersonServiceImpl implements PersonService {

    @Resource
    private PersonMapper personMapper;

    @Resource
    private PersonViewerSupport viewerSupport;

    @Resource
    private PersonQuerySupport querySupport;

    @Resource
    private PersonCommandSupport commandSupport;

    @Resource
    private PersonBatchDeleteSupport batchDeleteSupport;

    @Resource
    private PersonImportSupport importSupport;

    @Resource
    private PersonExportSupport exportSupport;

    @Resource
    private PersonTeamSupport personTeamSupport;

    @Resource
    private PersonSensitiveSupport sensitiveSupport;

    @Resource
    private IFileService fileService;

    @Override
    public Result<PageResult<PersonVO>> page(PersonQueryDTO dto) {
        //查询逻辑（筛选/子树/裁剪）在 PersonQuerySupport.page
        return Result.ok(querySupport.page(dto));
    }

    @Override
    public Result<PageResult<MemberOptionVO>> options(String keyword, String memberType,
                                                       Integer page, Integer size) {
        //M14 受限候选搜索：查询逻辑（列裁剪/过滤/分页兜底）在 PersonQuerySupport.options
        return Result.ok(querySupport.options(keyword, memberType, page, size));
    }

    @Override
    public Result<PersonVO> detail(Long id) {
        //1.查档案（Mapper SELF：不存在 1004）
        Person person = loadPerson(id);
        //2.访问门禁：部门管理员限子树、学生只能看自己；通过后再做四档视角裁剪
        viewerSupport.assertCanViewDetail(person);
        //3.四档视角判定（超管/本部门管理员/本人全量，本队队长大部分，其他受限）
        View view = viewerSupport.decideView(person);
        //4.技能 + 视角裁剪 + 组织名
        return Result.ok(buildDetailVo(person, view));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<PersonVO> create(PersonCreateDTO dto) {
        //1.越权封堵：部门管理员只能在自己组织子树内建档
        viewerSupport.assertAdminCanManage(dto.getOrgId());
        //2.学号查重（3001）与学工号查重；技能值校验在 saveSkills 内
        commandSupport.assertStudentNoFree(dto.getStudentNo(), null);
        commandSupport.assertJobNoFree(dto.getJobNo(), null);
        //3.组装入库（身份证 AES 加密 + 尾 4 位，默认在册/无账号）
        Person person = commandSupport.insertPerson(dto);
        //4.技能标签落库（可空）
        if (dto.getSkills() != null) {
            commandSupport.saveSkills(person.getId(), dto.getSkills());
        }
        //5.回显（创建者是管理员，全量视角）
        return Result.ok(buildDetailVo(person, View.FULL));
    }

    /** 详情口径组装：技能 + 视角裁剪 + 组织名（M02 详情 / M03 回显共用） */
    private PersonVO buildDetailVo(Person person, View view) {
        List<String> skills = querySupport.loadSkills(List.of(person.getId()))
                .getOrDefault(person.getId(), List.of());
        PersonVO vo = querySupport.toVO(person, skills, view, false);
        if (person.getOrgId() != null) {
            vo.setOrgName(querySupport.loadOrgNames(List.of(person.getOrgId())).get(person.getOrgId()));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PersonUpdateDTO dto) {
        //1.查档案（1004）
        Person person = loadPerson(id);
        //2.越权封堵：现组织与目标组织（换组织时）都必须在管理员的子树内
        viewerSupport.assertAdminCanManage(person.getOrgId());
        if (dto.getOrgId() != null) {
            viewerSupport.assertAdminCanManage(dto.getOrgId());
        }
        //3.学工号查重（排除自己）；学号不在入参——契约"不可修改"，3002 按契约占位定义
        commandSupport.assertJobNoFree(dto.getJobNo(), id);
        //4.部分更新（null 不改，身份证传了重新加密）
        commandSupport.applyUpdate(person, dto);
        //5.技能整组替换（null=不改，空列表=清空）
        if (dto.getSkills() != null) {
            commandSupport.saveSkills(id, dto.getSkills());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        //1.查档案（1004）
        Person person = loadPerson(id);
        //2.越权封堵
        viewerSupport.assertAdminCanManage(person.getOrgId());
        //3.队长拒删（3003）：活跃队伍的队长须先转移队长或解散
        if (personTeamSupport.isLeaderOfActiveTeam(id)) {
            throw new BizException(MemberErrorCode.MEMBER_IS_LEADER);
        }
        //4.当前登录账号拒删（3004）
        CurrentUser user = UserHolder.getUser();
        Long currentMemberId = viewerSupport.currentMemberId(user == null ? null : user.getUserId());
        if (id.equals(currentMemberId)) {
            throw new BizException(MemberErrorCode.MEMBER_IS_SELF);
        }
        //5.级联清理：逻辑删档案 + 物理删技能 + 清队伍关系 + 禁用其登录账号
        commandSupport.deletePersonCascade(person);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(PersonBatchDeleteDTO dto) {
        //M05B 整批同生共死：两阶段预检 + 级联在 PersonBatchDeleteSupport，
        //任一环节抛错本事务整体回滚——不出现"删了一半"的中间态
        batchDeleteSupport.deleteAll(dto);
    }

    @Override
    public void importTemplate(HttpServletResponse response) {
        //响应头、模板与导出的写出统一在 PersonExportSupport（与导入侧对称）
        exportSupport.writeTemplate(response);
    }

    @Override
    public Result<PersonImportVO> importExcel(MultipartFile file) {
        //⚠️ 仍不放 @Transactional：行级事务（NESTED）在 PersonImportSupport 内自持——
        //  生产=每行独立真事务，就算外层被包事务也只是退化为保存点；导入逐行独立，不需要外层事务
        return Result.ok(importSupport.importExcel(file));
    }

    @Override
    public void bindPhoto(Long id, PersonFileBindDTO dto) {
        bindFile(id, dto, true);
    }

    @Override
    public void bindSignature(Long id, PersonFileBindDTO dto) {
        bindFile(id, dto, false);
    }

    /** M08/M09 共用：档案存在 → 子树封堵 → 文件归属/扩展名/病毒闸门校验回填 */
    private void bindFile(Long id, PersonFileBindDTO dto, boolean photo) {
        Person person = loadPerson(id);
        viewerSupport.assertAdminCanManage(person.getOrgId());
        commandSupport.bindFile(person, dto.getFileId(), photo);
    }

    @Override
    public Result<PersonIdCardVO> idCard(Long id) {
        //1.查档案（1004）
        Person person = loadPerson(id);
        //2.敏感视角判定：超管/本部门管理员/本队队长/本人，不足 3010（controller 已挂审计）
        viewerSupport.assertCanViewSensitive(person);
        //3.解密返回（密钥轮换或脏数据导致解密失败返回 null，按"无明文"处理不报错）
        return Result.ok(new PersonIdCardVO(sensitiveSupport.decryptIdCard(person.getIdCard())));
    }

    @Override
    public void downloadPhoto(Long id, HttpServletResponse response) {
        downloadFile(id, true, response);
    }

    @Override
    public void downloadSignature(Long id, HttpServletResponse response) {
        downloadFile(id, false, response);
    }

    /** M11a/M11b 共用：权限同 M10（不足 3010）→ 附件存在性 → 走文件服务的病毒闸门流式回写 */
    private void downloadFile(Long id, boolean photo, HttpServletResponse response) {
        Person person = loadPerson(id);
        viewerSupport.assertCanViewSensitive(person);
        Long fileId = photo ? person.getPhotoFileId() : person.getSignatureFileId();
        if (fileId == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND,
                    photo ? "该人员未上传证件照" : "该人员未上传电子签名");
        }
        fileService.streamFile(fileId, response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSkills(Long id, List<String> skills) {
        //1.查档案（1004）
        Person person = loadPerson(id);
        //2.判定：本人或管理员（部门管理员限子树），其他 1003
        viewerSupport.assertCanEditSkills(person);
        //3.整组替换（值必须来自 SKILL 字典，校验在 saveSkills 内）
        commandSupport.saveSkills(id, skills);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindAccount(Long id, PersonAccountDTO dto) {
        //1.查档案（1004）+ 子树封堵
        Person person = loadPerson(id);
        viewerSupport.assertAdminCanManage(person.getOrgId());
        //2.已绑定不能重复绑
        if (person.getHasAccount() != null && person.getHasAccount() == 1) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "该人员已绑定登录账号");
        }
        //3.建账号（登录名默认链 + 初始密码 abc123 + 2003 查重在账号服务）并回写标记
        //（事务：建账号与 has_account 回写必须同生共死，否则出现"账号已建但标记未写"的卡死态）
        commandSupport.bindAccountFor(person, dto.getLoginName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeAccount(Long id, PersonAccountDTO dto) {
        //1.查档案（1004）+ 子树封堵
        Person person = loadPerson(id);
        viewerSupport.assertAdminCanManage(person.getOrgId());
        //2.必须已绑定
        if (person.getHasAccount() == null || person.getHasAccount() != 1) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "该人员未绑定登录账号");
        }
        //3.改登录名 / 重置密码（重置后旧 token 全部失效）
        commandSupport.changeAccountFor(person, dto);
    }

    @Override
    public void export(PersonQueryDTO dto, HttpServletResponse response) {
        //仅超管可调（controller 注解收口）；取数上限与行组装在 PersonExportSupport
        exportSupport.writeExport(dto, response);
    }

    /** 按 id 取档案，不存在统一 1004 */
    private Person loadPerson(Long id) {
        Person person = personMapper.selectById(id);
        if (person == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "人员不存在");
        }
        return person;
    }
}
