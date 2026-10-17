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
import com.viomanager.person.dto.PersonAccountDTO;
import com.viomanager.person.dto.PersonCreateDTO;
import com.viomanager.person.dto.PersonFileBindDTO;
import com.viomanager.person.dto.PersonIdCardVO;
import com.viomanager.person.dto.PersonImportVO;
import com.viomanager.person.dto.PersonQueryDTO;
import com.viomanager.person.dto.PersonUpdateDTO;
import com.viomanager.person.dto.PersonVO;
import com.viomanager.person.entity.Person;
import com.viomanager.person.mapper.PersonMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private PersonMapper personMapper;

    @Resource
    private PersonViewerSupport viewerSupport;

    @Resource
    private PersonQuerySupport querySupport;

    @Resource
    private PersonCommandSupport commandSupport;

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
        //1.分页参数兜底（规范 §2.3：默认 1/20，size 上限 100）
        int page = dto.getPage() == null || dto.getPage() < 1 ? 1 : dto.getPage();
        int size = dto.getSize() == null || dto.getSize() < 1 ? 20 : Math.min(dto.getSize(), MAX_PAGE_SIZE);
        //2.筛选拼装 + 部门管理员子树过滤（Mapper 改 SELF 后由服务层负责）
        QueryWrapper<Person> wrapper = querySupport.buildWrapper(dto);
        querySupport.applyDeptAdminSubtree(wrapper);
        //3.分页查询
        Page<Person> result = personMapper.selectPage(new Page<>(page, size), wrapper);
        List<Person> records = result.getRecords();
        //4.批量取技能与组织名（防 N+1）后按列表口径裁剪（身份证/证件照/签名/邮箱恒不进列表）
        Map<Long, List<String>> skillsMap = querySupport.loadSkills(
                records.stream().map(Person::getId).toList());
        Map<Long, String> orgNames = querySupport.loadOrgNames(
                records.stream().map(Person::getOrgId).filter(Objects::nonNull).distinct().toList());
        List<PersonVO> list = new ArrayList<>(records.size());
        for (Person person : records) {
            PersonVO vo = querySupport.toVO(person, skillsMap.get(person.getId()), View.FULL, true);
            vo.setOrgName(orgNames.get(person.getOrgId()));
            list.add(vo);
        }
        return Result.ok(PageResult.of(list, result.getTotal(), page, size));
    }

    @Override
    public Result<PersonVO> detail(Long id) {
        //1.查档案（Mapper SELF：任意登录人可查到行，可见范围靠出参裁剪；不存在 1004）
        Person person = loadPerson(id);
        //2.四档视角判定：超管/本部门管理员/本人全量，本队队长大部分，其他受限
        View view = viewerSupport.decideView(person);
        //3.技能 + 视角裁剪 + 组织名
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
    public void importTemplate(HttpServletResponse response) {
        //响应头、模板与导出的写出统一在 PersonExportSupport（与导入侧对称）
        exportSupport.writeTemplate(response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<PersonImportVO> importExcel(MultipartFile file) {
        //逐行校验/跳过/入库的完整口径在 PersonImportSupport（组织按名称、已存在学号跳过）
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
    public void bindAccount(Long id, PersonAccountDTO dto) {
        //1.查档案（1004）+ 子树封堵
        Person person = loadPerson(id);
        viewerSupport.assertAdminCanManage(person.getOrgId());
        //2.已绑定不能重复绑
        if (person.getHasAccount() != null && person.getHasAccount() == 1) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "该人员已绑定登录账号");
        }
        //3.建账号（登录名默认链 + 初始密码 abc123 + 2003 查重在账号服务）并回写标记
        commandSupport.bindAccountFor(person, dto.getLoginName());
    }

    @Override
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
