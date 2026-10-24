package com.viomanager.person.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.datascop.OrgScopeSupport;
import com.viomanager.person.dto.PersonCreateDTO;
import com.viomanager.person.dto.PersonImportVO;
import com.viomanager.person.entity.Person;
import com.viomanager.person.mapper.PersonMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * M07 Excel 批量导入支撑：逐行解析校验、建档并自动创建登录账号，返回 successCount / failCount / 逐行错误。
 * 契约口径（2026-10-24 拍板：导入自动建号）：
 *  - 建号规则复用 M13：登录名 = 学工号 → 学号（模板两值同列）、abc123、首登强制改密——学号/学工号列因此必填；
 *  - 学号已存在且有账号 → 跳过（重导幂等）；已存在但无账号 → 只补建账号（补 M13 漏绑存量），计入成功；
 *  - 建号失败（登录名被占等）→ 该行整体失败：行级事务回滚档案，errors 带行号与原因；
 *  - 组织按名称精确匹配且唯一、证件照/签名列读入忽略值、性别兼容中英文；表头按文字定位容忍列序调整，行号为 Excel 实际行号（表头第 1 行）。
 */
@Component
public class PersonImportSupport {

    /** 单次导入行数上限：防一个超大 Excel 把服务拖死（与既有交付口径一致） */
    private static final int MAX_IMPORT_ROWS = 5000;

    private static final Set<String> MEMBER_TYPES = Set.of("STUDENT", "TEACHER", "OTHER");

    @Resource
    private PersonCommandSupport personCommandSupport;

    @Resource
    private PersonMapper personMapper;

    @Resource
    private OrgScopeSupport orgScopeSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 行级事务（NESTED）：生产=每行独立真事务；测试外层有事务时退化为保存点，单行回滚不污染外层 */
    private final TransactionTemplate rowTx;

    public PersonImportSupport(PlatformTransactionManager txManager) {
        this.rowTx = new TransactionTemplate(txManager);
        this.rowTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }

    public PersonImportVO importExcel(MultipartFile file) {
        //1.越权封堵：导入是写操作，部门管理员只能往子树导人（逐行校验在 validateRow / backfillAccount）
        CurrentUser user = UserHolder.getUser();
        if (user == null || !"SUPER_ADMIN".equals(user.getRole()) && !"DEPT_ADMIN".equals(user.getRole())) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        CurrentUser deptAdmin = "DEPT_ADMIN".equals(user.getRole()) ? user : null;
        //2.解析 xlsx（文件空/格式错/解析失败在 read 内抛 1001）
        List<List<String>> rows = PersonExcelSupport.read(file);
        if (rows.size() < 2) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "Excel 没有数据行");
        }
        if (rows.size() - 1 > MAX_IMPORT_ROWS) {
            throw new BizException(CommonErrorCode.PARAM_INVALID,
                    "单次最多导入 " + MAX_IMPORT_ROWS + " 行，请分批上传");
        }
        //3.表头按文字定位列（改列序不用改代码；缺必备列直接判模板错）
        Map<String, Integer> colMap = headerIndex(rows.get(0));
        for (String required : new String[]{"姓名", "学号/学工号", "所属组织", "类型"}) {
            if (!colMap.containsKey(required)) {
                throw new BizException(CommonErrorCode.PARAM_INVALID,
                        "模板表头缺少「" + required + "」列，请下载最新模板填写");
            }
        }
        //4.逐行处理：合法建档并自动建号 / 已存在有账号跳过 / 已存在无账号补建 / 非法记错误
        int success = 0;
        List<PersonImportVO.RowError> errors = new ArrayList<>();
        Set<String> seenStudentNos = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            int excelRowNo = i + 1;
            List<String> row = rows.get(i);
            //4.1姓名必填
            if (StrUtil.isBlank(text(row, colMap, "姓名"))) {
                errors.add(new PersonImportVO.RowError(excelRowNo, "姓名不能为空"));
                continue;
            }
            //4.2学号必填：自动建号以学号/学工号列作登录名（M13 默认链），留空建不了账号整行失败
            String studentNo = StrUtil.trimToNull(text(row, colMap, "学号/学工号"));
            if (studentNo == null) {
                errors.add(new PersonImportVO.RowError(excelRowNo,
                        "学号/学工号不能为空（导入将按其自动创建登录账号）"));
                continue;
            }
            //4.3文件内重复学号跳过——不计成功也不计失败（与库里已存在同口径，已失败的行也算处理过）
            if (seenStudentNos.contains(studentNo)) {
                continue;
            }
            seenStudentNos.add(studentNo);
            //4.4库里已存在：有账号跳过（重导幂等）；无账号只补建账号（重导补存量口子，计成功）
            Person existing = findActiveByStudentNo(studentNo);
            if (existing != null) {
                if (existing.getHasAccount() != null && existing.getHasAccount() == 1) {
                    continue;
                }
                String backfillError = backfillAccount(existing, deptAdmin);
                if (backfillError == null) {
                    success++;
                } else {
                    errors.add(new PersonImportVO.RowError(excelRowNo, backfillError));
                }
                continue;
            }
            //4.5其余列校验，出问题记行号与原因
            String reason = validateRow(row, colMap, deptAdmin);
            if (reason != null) {
                errors.add(new PersonImportVO.RowError(excelRowNo, reason));
                continue;
            }
            //4.6建档 + 自动建号同一行级事务：建号失败整行回滚，不留"有档无号"半成品；并发撞唯一键兜底转逐行错误
            try {
                rowTx.executeWithoutResult(status -> {
                    Person person = personCommandSupport.insertPerson(toDto(row, colMap));
                    personCommandSupport.bindAccountFor(person, null);
                });
                success++;
            } catch (BizException e) {
                errors.add(new PersonImportVO.RowError(excelRowNo, "账号创建失败：" + e.getMessage()));
            } catch (DataIntegrityViolationException e) {
                errors.add(new PersonImportVO.RowError(excelRowNo, "数据冲突：学号/学工号已存在或字段超长"));
            }
        }
        return new PersonImportVO(success, errors.size(), errors);
    }

    /** 行内非姓名列的校验：返回 null 表示通过，否则是错误原因 */
    private String validateRow(List<String> row, Map<String, Integer> colMap, CurrentUser deptAdmin) {
        //1.长度对齐建表列宽——超长在 MySQL 严格模式会 Data too long 拖垮整批，必须提前逐行拦
        if (text(row, colMap, "姓名").trim().length() > 50) {
            return "姓名最长 50 字";
        }
        if (text(row, colMap, "学号/学工号").trim().length() > 32) {
            return "学号/学工号最长 32 字";
        }
        String college = text(row, colMap, "学院");
        if (college.length() > 100) {
            return "学院最长 100 字";
        }
        String major = text(row, colMap, "专业");
        if (major.length() > 100) {
            return "专业最长 100 字";
        }
        String className = text(row, colMap, "班级");
        if (className.length() > 100) {
            return "班级最长 100 字";
        }
        String nation = text(row, colMap, "民族");
        if (nation.length() > 32) {
            return "民族最长 32 字";
        }
        //2.手机号：可空，填了须合法
        String phone = text(row, colMap, "手机号");
        if (StrUtil.isNotBlank(phone) && !phone.trim().matches("^1[3-9]\\d{9}$")) {
            return "手机号格式不正确";
        }
        //3.身份证：可空，填了须 18 位
        String idCard = text(row, colMap, "身份证号");
        if (StrUtil.isNotBlank(idCard) && !idCard.trim().matches("\\d{17}[0-9Xx]")) {
            return "身份证号格式不正确";
        }
        //4.性别：可空（当 UNKNOWN），兼容中英文取值
        if (genderError(text(row, colMap, "性别"))) {
            return "性别取值不合法（男/女/MALE/FEMALE/留空）";
        }
        //5.邮箱：可空，填了须格式合法且不超列宽
        String email = StrUtil.trimToNull(text(row, colMap, "邮箱"));
        if (email != null && (email.length() > 100 || !email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$"))) {
            return "邮箱格式不正确";
        }
        //6.组织：按名称精确匹配且唯一，不存在或不唯一该行报错（契约原文）；
        //  部门管理员再校验目标组织在自己子树内（与 M03 建档的越权封堵对齐）
        String orgName = StrUtil.trimToNull(text(row, colMap, "所属组织"));
        Long orgId = orgIdByName(orgName);
        if (orgId == null) {
            return "所属组织不存在或名称不唯一";
        }
        if (deptAdmin != null && !orgScopeSupport.inSubtree(deptAdmin.getOrgId(), orgId)) {
            return "无权向该组织导入人员";
        }
        //7.类型：可空（默认 STUDENT），填了须合法
        String memberType = StrUtil.trimToNull(text(row, colMap, "类型"));
        if (memberType != null && !MEMBER_TYPES.contains(memberType)) {
            return "类型取值不合法（STUDENT/TEACHER/OTHER）";
        }
        return null;
    }

    /** 合法行 → 新建 DTO（复用 insertPerson 的组装与加密逻辑；技能列模板没有，恒为空） */
    private PersonCreateDTO toDto(List<String> row, Map<String, Integer> colMap) {
        PersonCreateDTO dto = new PersonCreateDTO();
        dto.setName(text(row, colMap, "姓名").trim());
        dto.setStudentNo(StrUtil.trimToNull(text(row, colMap, "学号/学工号")));
        dto.setPhone(StrUtil.trimToNull(text(row, colMap, "手机号")));
        dto.setCollege(StrUtil.trimToNull(text(row, colMap, "学院")));
        dto.setMajor(StrUtil.trimToNull(text(row, colMap, "专业")));
        dto.setClassName(StrUtil.trimToNull(text(row, colMap, "班级")));
        dto.setGender(normalizeGender(text(row, colMap, "性别")));
        dto.setNation(StrUtil.trimToNull(text(row, colMap, "民族")));
        dto.setIdCard(StrUtil.trimToNull(text(row, colMap, "身份证号")));
        dto.setEmail(StrUtil.trimToNull(text(row, colMap, "邮箱")));
        dto.setOrgId(orgIdByName(StrUtil.trimToNull(text(row, colMap, "所属组织"))));
        dto.setMemberType(StrUtil.blankToDefault(StrUtil.trimToNull(text(row, colMap, "类型")), "STUDENT"));
        return dto;
    }

    /** 已存在但无账号的行：按 M13 规则补建账号；返回 null=成功，否则=失败原因 */
    private String backfillAccount(Person existing, CurrentUser deptAdmin) {
        //1.部门管理员只能给子树内的人补号（以档案真实组织为准，不信 Excel 行里的组织列）
        if (deptAdmin != null && !orgScopeSupport.inSubtree(deptAdmin.getOrgId(), existing.getOrgId())) {
            return "无权为该组织人员补建账号";
        }
        //2.补建：登录名走 M13 默认链（学工号→学号），被占时 2003 在账号服务内抛，落进行级事务
        try {
            rowTx.executeWithoutResult(status -> personCommandSupport.bindAccountFor(existing, null));
            return null;
        } catch (BizException e) {
            return "账号创建失败：" + e.getMessage();
        }
    }

    /** 按学号查在册档案（@TableLogic 自动滤已删）；正常最多一条，异常多条按不存在走建档兜错 */
    private Person findActiveByStudentNo(String studentNo) {
        List<Person> persons = personMapper.selectList(
                new QueryWrapper<Person>().eq("student_no", studentNo));
        return persons.size() == 1 ? persons.get(0) : null;
    }

    /** 组织名称 → ID：必须精确匹配且唯一（重名组织会让导入有歧义，按契约报错） */
    private Long orgIdByName(String orgName) {
        if (orgName == null) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM sys_org WHERE name = ? AND deleted = 0", Long.class, orgName);
        return ids.size() == 1 ? ids.get(0) : null;
    }

    private boolean genderError(String raw) {
        return normalizeGender(raw) == null && StrUtil.isNotBlank(raw);
    }

    /** 男→MALE / 女→FEMALE / 空或 UNKNOWN→UNKNOWN，其余返回 null（非法） */
    private String normalizeGender(String raw) {
        String trimmed = StrUtil.trimToNull(raw);
        if (trimmed == null) {
            return "UNKNOWN";
        }
        return switch (trimmed) {
            case "男", "MALE" -> "MALE";
            case "女", "FEMALE" -> "FEMALE";
            case "UNKNOWN" -> "UNKNOWN";
            default -> null;
        };
    }

    /** 表头行 → 列名到下标的映射（重复列名取第一个） */
    private Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = StrUtil.trimToNull(header.get(i));
            if (name != null) {
                map.putIfAbsent(name, i);
            }
        }
        return map;
    }

    /** 取一行某列的文本（列缺失或越界返回空串） */
    private String text(List<String> row, Map<String, Integer> colMap, String column) {
        Integer index = colMap.get(column);
        if (index == null || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value;
    }
}
