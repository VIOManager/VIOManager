package com.viomanager.person.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.dict.service.IDictService;
import com.viomanager.dict.vo.DictItemVO;
import com.viomanager.file.service.IFileService;
import com.viomanager.file.vo.FileUploadVO;
import com.viomanager.person.dto.PersonCreateDTO;
import com.viomanager.person.dto.PersonQueryDTO;
import com.viomanager.person.dto.PersonUpdateDTO;
import com.viomanager.person.dto.PersonVO;
import com.viomanager.person.entity.MemberSkill;
import com.viomanager.person.entity.Person;
import com.viomanager.person.mapper.MemberSkillMapper;
import com.viomanager.person.mapper.PersonMapper;
import com.viomanager.person.support.PersonExcelSupport;
import com.viomanager.person.support.PersonSensitiveSupport;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人员档案业务实现（P01–P08）：查重、加解密、脱敏、Excel 解析组装全部在这里。
 * 数据库操作只注入本模块的 PersonMapper / MemberSkillMapper；文件上传复用 file 模块，
 * 技能标签字典复用 dict 模块，均不重复造轮子。
 * 脱敏口径（规范 §5.2 数据权限的第二层）：超管看完整手机号与身份证明文，
 * 其他角色手机号中间 4 位打星、身份证只回传尾号 4 位——controller 注解管进不进得来，
 * 这层管进来能看到什么。
 */
@Service
public class PersonServiceImpl implements PersonService {

    /** 单次导出行数上限：防止无筛选全表导出拖垮服务，超量请用筛选条件缩小范围 */
    private static final int MAX_EXPORT_ROWS = 10000;

    /** 分页条数上限（规范 §2.3） */
    private static final int MAX_PAGE_SIZE = 100;

    /** 单次导入行数上限：xlsx 会整包读入内存，防止超大文件拖垮服务 */
    private static final int MAX_IMPORT_ROWS = 5000;

    @Resource
    private PersonMapper personMapper;

    @Resource
    private MemberSkillMapper memberSkillMapper;

    @Resource
    private IFileService fileService;

    @Resource
    private IDictService dictService;

    @Resource
    private PersonSensitiveSupport sensitive;

    // ------------------------------------------------------------ P01 新增

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<PersonVO> create(PersonCreateDTO dto) {
        //1.唯一性校验：学号、学工号全局不可重复（建表唯一键的业务前置校验，给出友好报错）
        checkStudentNoFree(blankToNull(dto.getStudentNo()), null);
        checkJobNoFree(blankToNull(dto.getJobNo()), null);
        //2.DTO → 实体；身份证密文入库，尾号 4 位单存一列供列表脱敏展示
        String idCard = blankToNull(dto.getIdCard());
        Person person = new Person()
                .setName(dto.getName().trim())
                .setStudentNo(blankToNull(dto.getStudentNo()))
                .setJobNo(blankToNull(dto.getJobNo()))
                .setPhone(blankToNull(dto.getPhone()))
                .setCollege(blankToNull(dto.getCollege()))
                .setMajor(blankToNull(dto.getMajor()))
                .setClassName(blankToNull(dto.getClassName()))
                .setGender(blankToNull(dto.getGender()))
                .setNation(blankToNull(dto.getNation()))
                .setIdCard(sensitive.encryptIdCard(idCard))
                .setIdCardLast4(sensitive.last4OfIdCard(idCard))
                .setEmail(blankToNull(dto.getEmail()))
                .setOrgId(dto.getOrgId())
                .setMemberType(StrUtil.isBlank(dto.getMemberType()) ? "STUDENT" : dto.getMemberType())
                .setStatus(1)
                .setHasAccount(0)
                .setRemark(blankToNull(dto.getRemark()));
        personMapper.insert(person);
        //3.技能标签落 member_skill（同事务，档案删了标签跟着逻辑删）
        saveSkills(person.getId(), dto.getSkills());
        return Result.ok(toVO(person, loadSkills(List.of(person.getId())).get(person.getId()), isSuperAdmin()));
    }

    // ------------------------------------------------------------ P02 编辑

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PersonUpdateDTO dto) {
        //1.存在性校验（selectById 自动带 deleted=0，已删档案等同不存在）
        Person person = personMapper.selectById(id);
        if (person == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "人员不存在");
        }
        //2.学工号改了才查重；学号不可修改（DTO 就没让传，注释口径见建表脚本）
        String jobNo = blankToNull(dto.getJobNo());
        if (jobNo != null && !jobNo.equals(person.getJobNo())) {
            checkJobNoFree(jobNo, id);
        }
        //3.部分更新：传了才改（updateById 默认跳过 null 字段）
        if (StrUtil.isNotBlank(dto.getName())) {
            person.setName(dto.getName().trim());
        }
        person.setJobNo(jobNo == null ? person.getJobNo() : jobNo);
        if (blankToNull(dto.getPhone()) != null) {
            person.setPhone(dto.getPhone().trim());
        }
        if (blankToNull(dto.getCollege()) != null) {
            person.setCollege(dto.getCollege().trim());
        }
        if (blankToNull(dto.getMajor()) != null) {
            person.setMajor(dto.getMajor().trim());
        }
        if (blankToNull(dto.getClassName()) != null) {
            person.setClassName(dto.getClassName().trim());
        }
        if (blankToNull(dto.getGender()) != null) {
            person.setGender(dto.getGender());
        }
        if (blankToNull(dto.getNation()) != null) {
            person.setNation(dto.getNation().trim());
        }
        //4.身份证传了就重新加密覆盖；不传保持原密文
        String idCard = blankToNull(dto.getIdCard());
        if (idCard != null) {
            person.setIdCard(sensitive.encryptIdCard(idCard));
            person.setIdCardLast4(sensitive.last4OfIdCard(idCard));
        }
        if (blankToNull(dto.getEmail()) != null) {
            person.setEmail(dto.getEmail().trim());
        }
        if (dto.getOrgId() != null) {
            person.setOrgId(dto.getOrgId());
        }
        if (blankToNull(dto.getMemberType()) != null) {
            person.setMemberType(dto.getMemberType());
        }
        if (dto.getStatus() != null) {
            person.setStatus(dto.getStatus());
        }
        if (blankToNull(dto.getRemark()) != null) {
            person.setRemark(dto.getRemark().trim());
        }
        if (personMapper.updateById(person) == 0) {
            throw new BizException(CommonErrorCode.CONCURRENT_MODIFIED, "人员信息已被他人修改，请刷新后重试");
        }
        //5.技能标签：null = 不改；其余整组替换
        if (dto.getSkills() != null) {
            saveSkills(id, dto.getSkills());
        }
    }

    // ------------------------------------------------------------ P03 删除

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Person person = personMapper.selectById(id);
        if (person == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "人员不存在");
        }
        //技能标签先逻辑删，再删档案本身，均走 @TableLogic 转 UPDATE deleted=1
        memberSkillMapper.delete(new QueryWrapper<MemberSkill>().eq("member_id", id));
        personMapper.deleteById(id);
        //证件照等附件保留在 file_object / MinIO，由文件模块统一管理，不在这里物理清理
    }

    // ------------------------------------------------------------ P04 分页列表

    @Override
    public Result<PageResult<PersonVO>> page(PersonQueryDTO dto) {
        int page = dto.getPage() == null || dto.getPage() < 1 ? 1 : dto.getPage();
        int size = dto.getSize() == null || dto.getSize() < 1 ? 20 : Math.min(dto.getSize(), MAX_PAGE_SIZE);
        Page<Person> result = personMapper.selectPage(new Page<>(page, size), buildWrapper(dto));
        //✅ 核心权限逻辑：出参按当前登录角色脱敏
        boolean full = isSuperAdmin();
        Map<Long, List<String>> skillsMap = loadSkills(
                result.getRecords().stream().map(Person::getId).toList());
        List<PersonVO> vos = result.getRecords().stream()
                .map(person -> toVO(person, skillsMap.get(person.getId()), full))
                .toList();
        return Result.ok(PageResult.of(vos, result.getTotal(), result.getCurrent(), result.getSize()));
    }

    // ------------------------------------------------------------ P05 详情

    @Override
    public Result<PersonVO> detail(Long id) {
        Person person = personMapper.selectById(id);
        if (person == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "人员不存在");
        }
        return Result.ok(toVO(person,
                loadSkills(List.of(id)).get(id), isSuperAdmin()));
    }

    // ------------------------------------------------------------ P06 Excel 导入

    @Override
    public Result<PersonExcelSupport.ImportResult> importExcel(MultipartFile file) {
        List<List<String>> rows = PersonExcelSupport.read(file);
        if (rows.size() <= 1) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "Excel 中没有可导入的数据行");
        }
        if (rows.size() - 1 > MAX_IMPORT_ROWS) {
            throw new BizException(CommonErrorCode.PARAM_INVALID,
                    "单次最多导入 " + MAX_IMPORT_ROWS + " 行，请分批上传");
        }
        //1.表头 → 列下标（列顺序无关，按表头名取值）
        Map<String, Integer> col = new HashMap<>();
        List<String> headers = rows.get(0);
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (StrUtil.isNotBlank(header)) {
                col.put(header.trim(), i);
            }
        }
        if (!col.containsKey("姓名")) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "缺少必需的「姓名」列，请使用导出模板");
        }
        //2.逐行解析入库：合法行提交、非法行记错误（不做整体回滚，方便管理员照错误单改表重传）
        Map<String, String> skillLabels = loadSkillLabelMap();
        Set<String> fileStudentNos = new HashSet<>();
        List<String> errors = new ArrayList<>();
        int total = rows.size() - 1;
        int success = 0;
        for (int i = 1; i < rows.size(); i++) {
            int rowNo = i + 1;
            List<String> row = rows.get(i);
            try {
                Person person = buildPersonFromRow(row, col);
                String studentNo = person.getStudentNo();
                if (studentNo != null) {
                    if (!fileStudentNos.add(studentNo)) {
                        throw new BizException(CommonErrorCode.PARAM_INVALID, "学号在文件内重复：" + studentNo);
                    }
                    checkStudentNoFree(studentNo, null);
                }
                if (person.getJobNo() != null) {
                    checkJobNoFree(person.getJobNo(), null);
                }
                personMapper.insert(person);
                saveSkills(person.getId(), rowSkills(row, col), skillLabels);
                success++;
            } catch (BizException e) {
                errors.add("第 " + rowNo + " 行：" + e.getMessage());
            } catch (RuntimeException e) {
                errors.add("第 " + rowNo + " 行：数据写入失败，请检查该行格式");
            }
        }
        return Result.ok(new PersonExcelSupport.ImportResult(total, success, errors));
    }

    // ------------------------------------------------------------ P07 Excel 导出

    @Override
    public void export(PersonQueryDTO dto, HttpServletResponse response) {
        //1.按筛选条件查库（无分页，行数封顶；超出上限提示改筛选）
        List<Person> people = personMapper.selectList(
                buildWrapper(dto).last("LIMIT " + MAX_EXPORT_ROWS));
        //2.导出只有超级管理员才给完整敏感信息（接口本身已限超管，这里按角色再判一次防将来放开注解）
        boolean full = isSuperAdmin();
        Map<Long, List<String>> skillsMap = loadSkills(
                people.stream().map(Person::getId).toList());
        Map<String, String> skillLabels = loadSkillLabelMap();
        List<String> headers = List.of("姓名", "学号", "学工号", "学院", "专业", "班级", "性别", "民族",
                "身份证号", "手机号", "邮箱", "组织ID", "人员类型", "状态", "技能标签", "备注", "创建时间");
        List<List<String>> rows = new ArrayList<>();
        for (Person person : people) {
            List<String> skills = skillsMap.getOrDefault(person.getId(), List.of());
            String skillText = skills.stream()
                    .map(value -> skillLabels.getOrDefault(value, value))
                    .collect(Collectors.joining("、"));
            rows.add(List.of(
                    nvl(person.getName()),
                    nvl(person.getStudentNo()),
                    nvl(person.getJobNo()),
                    nvl(person.getCollege()),
                    nvl(person.getMajor()),
                    nvl(person.getClassName()),
                    genderText(person.getGender()),
                    nvl(person.getNation()),
                    full ? nvl(sensitive.decryptIdCard(person.getIdCard())) : "",
                    full ? nvl(person.getPhone()) : nvl(sensitive.maskPhone(person.getPhone())),
                    nvl(person.getEmail()),
                    person.getOrgId() == null ? "" : String.valueOf(person.getOrgId()),
                    memberTypeText(person.getMemberType()),
                    person.getStatus() != null && person.getStatus() == 1 ? "在册" : "离册",
                    skillText,
                    nvl(person.getRemark()),
                    person.getCreatedAt() == null ? "" : person.getCreatedAt().toLocalDate().toString()));
        }
        //3.xlsx 写入响应（文件名 URL 编码，兼容中文）
        String fileName = URLEncoder.encode("人员档案_" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        try (OutputStream out = response.getOutputStream()) {
            PersonExcelSupport.write(headers, rows, out);
        } catch (IOException e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "导出文件写入失败");
        }
    }

    // ------------------------------------------------------------ P08 证件照上传绑定

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindPhoto(Long id, MultipartFile file) {
        Person person = personMapper.selectById(id);
        if (person == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "人员不存在");
        }
        //复用文件模块：白名单/大小/病毒扫描/MinIO 落盘/落库都由它负责，本模块只拿 fileId 写档案
        Result<FileUploadVO> uploaded = fileService.upload(file, "PERSON_PHOTO", id);
        if (uploaded == null || uploaded.getData() == null) {
            throw new BizException(
                    uploaded == null ? CommonErrorCode.INTERNAL_ERROR.getCode() : uploaded.getCode(),
                    "证件照上传失败");
        }
        UpdateWrapper<Person> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).set("photo_file_id", uploaded.getData().getFileId());
        personMapper.update(null, wrapper);
    }

    // ------------------------------------------------------------ 私有方法：查询组装与校验

    /** 列表/导出共用的查询条件拼接（模糊搜索 + 精确筛选） */
    private QueryWrapper<Person> buildWrapper(PersonQueryDTO dto) {
        QueryWrapper<Person> wrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(dto.getName())) {
            wrapper.like("name", dto.getName().trim());
        }
        if (StrUtil.isNotBlank(dto.getStudentNo())) {
            wrapper.like("student_no", dto.getStudentNo().trim());
        }
        if (StrUtil.isNotBlank(dto.getCollege())) {
            wrapper.like("college", dto.getCollege().trim());
        }
        if (StrUtil.isNotBlank(dto.getMajor())) {
            wrapper.like("major", dto.getMajor().trim());
        }
        if (StrUtil.isNotBlank(dto.getMemberType())) {
            wrapper.eq("member_type", dto.getMemberType());
        }
        if (dto.getStatus() != null) {
            wrapper.eq("status", dto.getStatus());
        }
        if (dto.getOrgId() != null) {
            wrapper.eq("org_id", dto.getOrgId());
        }
        wrapper.orderByDesc("created_at");
        return wrapper;
    }

    /** 学号全局唯一（@TableLogic 自动过滤已删行）；excludeId 用于编辑时跳过本人 */
    private void checkStudentNoFree(String studentNo, Long excludeId) {
        if (studentNo == null) {
            return;
        }
        QueryWrapper<Person> wrapper = new QueryWrapper<Person>().eq("student_no", studentNo);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        if (personMapper.selectCount(wrapper) > 0) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "学号已存在：" + studentNo);
        }
    }

    private void checkJobNoFree(String jobNo, Long excludeId) {
        if (jobNo == null) {
            return;
        }
        QueryWrapper<Person> wrapper = new QueryWrapper<Person>().eq("job_no", jobNo);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        if (personMapper.selectCount(wrapper) > 0) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "学工号已存在：" + jobNo);
        }
    }

    // ------------------------------------------------------------ 私有方法：技能标签

    /** 覆盖式保存技能标签：先逻辑删旧行再插新行（前端整组提交） */
    private void saveSkills(Long memberId, List<String> skills) {
        saveSkills(memberId, skills, loadSkillLabelMap());
    }

    private void saveSkills(Long memberId, List<String> skills, Map<String, String> skillLabels) {
        if (skills == null) {
            return;
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String value : skills) {
            if (StrUtil.isNotBlank(value)) {
                distinct.add(value.trim());
            }
        }
        //整组替换前物理删旧行（物理删除原因见 MemberSkillMapper.deleteByMemberId 的唯一键说明）
        memberSkillMapper.deleteByMemberId(memberId);
        for (String value : distinct) {
            memberSkillMapper.insert(new MemberSkill()
                    .setMemberId(memberId)
                    .setSkillValue(value)
                    .setSkillLabel(skillLabels.getOrDefault(value, value)));
        }
    }

    /** 批量取技能标签，按 memberId 分组（一次 in 查询，避免列表 N+1） */
    private Map<Long, List<String>> loadSkills(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        return memberSkillMapper.selectList(
                        new QueryWrapper<MemberSkill>().in("member_id", memberIds))
                .stream()
                .collect(Collectors.groupingBy(MemberSkill::getMemberId,
                        Collectors.mapping(MemberSkill::getSkillValue, Collectors.toList())));
    }

    /** SKILL 字典 value → label，技能标签入库冗余展示名用；字典不可用时按原值兜底 */
    private Map<String, String> loadSkillLabelMap() {
        Result<List<DictItemVO>> result = dictService.listItems("SKILL");
        if (result == null || result.getCode() != Result.SUCCESS_CODE || result.getData() == null) {
            return Map.of();
        }
        return result.getData().stream()
                .filter(item -> StrUtil.isNotBlank(item.getItemValue()))
                .collect(Collectors.toMap(DictItemVO::getItemValue,
                        item -> StrUtil.blankToDefault(item.getItemLabel(), item.getItemValue()),
                        (first, second) -> first));
    }

    // ------------------------------------------------------------ 私有方法：Excel 行 ↔ 实体

    /** 列名 → 单元格值（列不存在或超出该行范围返回 null，兼容短行） */
    private String cell(List<String> row, Map<String, Integer> col, String name) {
        Integer index = col.get(name);
        if (index == null || index >= row.size()) {
            return null;
        }
        return row.get(index);
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    /** Excel 一行 → Person 实体（身份证已加密），格式错误抛 BizException 由导入循环记行 */
    private Person buildPersonFromRow(List<String> row, Map<String, Integer> col) {
        String name = blankToNull(cell(row, col, "姓名"));
        if (name == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "姓名不能为空");
        }
        String phone = blankToNull(cell(row, col, "手机号"));
        if (phone != null && !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "手机号格式不正确：" + phone);
        }
        String idCard = blankToNull(cell(row, col, "身份证号"));
        if (idCard != null && !idCard.matches("\\d{17}[0-9Xx]")) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "身份证号格式不正确：" + idCard);
        }
        String email = blankToNull(cell(row, col, "邮箱"));
        if (email != null && !email.matches("^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$")) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "邮箱格式不正确：" + email);
        }
        Long orgId = null;
        String orgRaw = blankToNull(cell(row, col, "组织ID"));
        if (orgRaw != null) {
            try {
                orgId = Long.parseLong(orgRaw);
            } catch (NumberFormatException e) {
                throw new BizException(CommonErrorCode.PARAM_INVALID, "组织ID 不是数字：" + orgRaw);
            }
        }
        return new Person()
                .setName(name)
                .setStudentNo(blankToNull(cell(row, col, "学号")))
                .setJobNo(blankToNull(cell(row, col, "学工号")))
                .setPhone(phone)
                .setCollege(blankToNull(cell(row, col, "学院")))
                .setMajor(blankToNull(cell(row, col, "专业")))
                .setClassName(blankToNull(cell(row, col, "班级")))
                .setGender(convertGender(cell(row, col, "性别")))
                .setNation(blankToNull(cell(row, col, "民族")))
                .setIdCard(sensitive.encryptIdCard(idCard))
                .setIdCardLast4(sensitive.last4OfIdCard(idCard))
                .setEmail(email)
                .setOrgId(orgId)
                .setMemberType(convertMemberType(cell(row, col, "人员类型")))
                .setStatus(1)
                .setHasAccount(0)
                .setRemark(blankToNull(cell(row, col, "备注")));
    }

    /** 「技能标签」列：分隔符支持 ； ; 、 ， , */
    private List<String> rowSkills(List<String> row, Map<String, Integer> col) {
        String raw = blankToNull(cell(row, col, "技能标签"));
        if (raw == null) {
            return null;
        }
        return Arrays.stream(raw.split("[；;、，,]"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    /** 性别列宽容转换：男/MALE→MALE，女/FEMALE→FEMALE，其余一律 UNKNOWN */
    private String convertGender(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String value = raw.trim();
        return switch (value) {
            case "男", "MALE" -> "MALE";
            case "女", "FEMALE" -> "FEMALE";
            default -> "UNKNOWN";
        };
    }

    /** 人员类型列转换：学生/STUDENT→STUDENT，教师·老师/TEACHER→TEACHER，其余 OTHER；空默认 STUDENT */
    private String convertMemberType(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "STUDENT";
        }
        String value = raw.trim();
        return switch (value) {
            case "学生", "STUDENT" -> "STUDENT";
            case "教师", "老师", "TEACHER" -> "TEACHER";
            default -> "OTHER";
        };
    }

    private String genderText(String gender) {
        if (gender == null) {
            return "";
        }
        return switch (gender) {
            case "MALE" -> "男";
            case "FEMALE" -> "女";
            default -> "未知";
        };
    }

    private String memberTypeText(String memberType) {
        if (memberType == null) {
            return "";
        }
        return switch (memberType) {
            case "STUDENT" -> "学生";
            case "TEACHER" -> "教师";
            default -> "其他";
        };
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    // ------------------------------------------------------------ 私有方法：角色与脱敏

    /** 当前登录人是否超管（未登录按非超管处理，多脱敏不少脱敏） */
    private boolean isSuperAdmin() {
        CurrentUser user = UserHolder.getUser();
        return user != null && "SUPER_ADMIN".equals(user.getRole());
    }

    /**
     * 实体 → VO + 按角色脱敏（P01/P04/P05 共用）：
     * 超管：手机号完整、身份证返回解密明文；
     * 其他角色：手机号中间 4 位打星、身份证不出明文（前端用 idCardLast4 展示）。
     */
    private PersonVO toVO(Person person, List<String> skills, boolean full) {
        PersonVO vo = new PersonVO();
        vo.setId(person.getId());
        vo.setName(person.getName());
        vo.setStudentNo(person.getStudentNo());
        vo.setJobNo(person.getJobNo());
        vo.setCollege(person.getCollege());
        vo.setMajor(person.getMajor());
        vo.setClassName(person.getClassName());
        vo.setGender(person.getGender());
        vo.setNation(person.getNation());
        vo.setPhone(full ? person.getPhone() : sensitive.maskPhone(person.getPhone()));
        vo.setIdCard(full ? sensitive.decryptIdCard(person.getIdCard()) : null);
        vo.setIdCardLast4(person.getIdCardLast4());
        vo.setEmail(person.getEmail());
        vo.setPhotoFileId(person.getPhotoFileId());
        vo.setSignatureFileId(person.getSignatureFileId());
        vo.setOrgId(person.getOrgId());
        vo.setMemberType(person.getMemberType());
        vo.setStatus(person.getStatus());
        vo.setHasAccount(person.getHasAccount());
        vo.setSkills(skills == null ? List.of() : skills);
        vo.setRemark(person.getRemark());
        vo.setCreatedAt(person.getCreatedAt());
        vo.setUpdatedAt(person.getUpdatedAt());
        return vo;
    }
}
