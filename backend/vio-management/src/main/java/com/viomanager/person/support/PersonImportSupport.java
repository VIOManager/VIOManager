package com.viomanager.person.support;

import cn.hutool.core.util.StrUtil;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.person.dto.PersonCreateDTO;
import com.viomanager.person.dto.PersonImportVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * M07 Excel 批量导入支撑：逐行解析校验入库，返回 successCount / failCount / 逐行错误。
 * 契约口径：
 *  - 组织按名称精确匹配 sys_org.name，不存在或不唯一该行报错（不是按 ID）；
 *  - 学号已存在的行跳过——不计成功也不计失败（可重复导入幂等，跳过行静默）；
 *  - 证件照/电子签名列读入但忽略值（照片走 M08/M09 文件服务回填），留空不报错；
 *  - 性别兼容 男/女/MALE/FEMALE/空（空当 UNKNOWN）。
 * 表头按文字定位列，容忍模板列顺序调整；行号为 Excel 实际行号（表头是第 1 行）。
 */
@Component
public class PersonImportSupport {

    /** 单次导入行数上限：防一个超大 Excel 把服务拖死（与既有交付口径一致） */
    private static final int MAX_IMPORT_ROWS = 5000;

    private static final Set<String> MEMBER_TYPES = Set.of("STUDENT", "TEACHER", "OTHER");

    @Resource
    private PersonCommandSupport personCommandSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public PersonImportVO importExcel(MultipartFile file) {
        //1.解析 xlsx（文件空/格式错/解析失败在 read 内抛 1001）
        List<List<String>> rows = PersonExcelSupport.read(file);
        if (rows.size() < 2) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "Excel 没有数据行");
        }
        if (rows.size() - 1 > MAX_IMPORT_ROWS) {
            throw new BizException(CommonErrorCode.PARAM_INVALID,
                    "单次最多导入 " + MAX_IMPORT_ROWS + " 行，请分批上传");
        }
        //2.表头按文字定位列（改列序不用改代码；缺必备列直接判模板错）
        Map<String, Integer> colMap = headerIndex(rows.get(0));
        for (String required : new String[]{"姓名", "学号/学工号", "所属组织", "类型"}) {
            if (!colMap.containsKey(required)) {
                throw new BizException(CommonErrorCode.PARAM_INVALID,
                        "模板表头缺少「" + required + "」列，请下载最新模板填写");
            }
        }
        //3.逐行处理：合法入库 / 已存在跳过 / 非法记错误
        int success = 0;
        List<PersonImportVO.RowError> errors = new ArrayList<>();
        Set<String> seenStudentNos = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            int excelRowNo = i + 1;
            List<String> row = rows.get(i);
            String cell = text(row, colMap, "姓名");
            //3.1姓名必填
            if (StrUtil.isBlank(cell)) {
                errors.add(new PersonImportVO.RowError(excelRowNo, "姓名不能为空"));
                continue;
            }
            //3.2学号：已存在（库里或本文件里）跳过——契约原文，不计成功也不计失败
            String studentNo = StrUtil.trimToNull(text(row, colMap, "学号/学工号"));
            if (studentNo != null && (seenStudentNos.contains(studentNo) || studentNoExists(studentNo))) {
                continue;
            }
            //3.3其余列校验，出问题记行号与原因
            String reason = validateRow(row, colMap);
            if (reason != null) {
                errors.add(new PersonImportVO.RowError(excelRowNo, reason));
                continue;
            }
            //3.4组装入库（身份证加密在 insertPerson 内）
            personCommandSupport.insertPerson(toDto(row, colMap));
            if (studentNo != null) {
                seenStudentNos.add(studentNo);
            }
            success++;
        }
        return new PersonImportVO(success, errors.size(), errors);
    }

    /** 行内非姓名列的校验：返回 null 表示通过，否则是错误原因 */
    private String validateRow(List<String> row, Map<String, Integer> colMap) {
        //1.手机号：可空，填了须合法
        String phone = text(row, colMap, "手机号");
        if (StrUtil.isNotBlank(phone) && !phone.trim().matches("^1[3-9]\\d{9}$")) {
            return "手机号格式不正确";
        }
        //2.身份证：可空，填了须 18 位
        String idCard = text(row, colMap, "身份证号");
        if (StrUtil.isNotBlank(idCard) && !idCard.trim().matches("\\d{17}[0-9Xx]")) {
            return "身份证号格式不正确";
        }
        //3.性别：可空（当 UNKNOWN），兼容中英文取值
        if (genderError(text(row, colMap, "性别"))) {
            return "性别取值不合法（男/女/MALE/FEMALE/留空）";
        }
        //4.组织：按名称精确匹配，不存在或不唯一该行报错（契约原文）
        String orgName = StrUtil.trimToNull(text(row, colMap, "所属组织"));
        if (orgName == null || orgIdByName(orgName) == null) {
            return "所属组织不存在或名称不唯一";
        }
        //5.类型：可空（默认 STUDENT），填了须合法
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

    private boolean studentNoExists(String studentNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member WHERE student_no = ? AND deleted = 0",
                Integer.class, studentNo);
        return count != null && count > 0;
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
