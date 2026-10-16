package com.viomanager.person.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 新增人员入参（P01）：超管在后台新增人员档案时前端提交的数据。
 * 只接收、只校验，不做业务判断（学号查重、身份证加密在 service 层做）。
 * 身份证号为明文入参——服务端加密入库；证件照不在此接口（走 P08 上传接口拿 fileId）。
 */
@Data
public class PersonCreateDTO {

    /** 姓名（必填） */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名最长 50 字")
    private String name;

    /** 学号（可空；唯一，创建后不可修改） */
    @Size(max = 32, message = "学号最长 32 字")
    private String studentNo;

    /** 学工号（可空；唯一，登录名默认取此值） */
    @Size(max = 32, message = "学工号最长 32 字")
    private String jobNo;

    /** 手机号（可空；填了必须合法。⚠️ 敏感字段：非超管出参脱敏） */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 学院 */
    @Size(max = 100, message = "学院最长 100 字")
    private String college;

    /** 专业 */
    @Size(max = 100, message = "专业最长 100 字")
    private String major;

    /** 班级 */
    @Size(max = 100, message = "班级最长 100 字")
    private String className;

    /** 性别：MALE 男 / FEMALE 女 / UNKNOWN 未知 */
    @Pattern(regexp = "MALE|FEMALE|UNKNOWN", message = "性别取值不合法")
    private String gender;

    /** 民族 */
    @Size(max = 32, message = "民族最长 32 字")
    private String nation;

    /** 身份证号（18 位；⚠️ 敏感字段：明文入参，服务端 AES 加密入库） */
    @Pattern(regexp = "\\d{17}[0-9Xx]", message = "身份证号格式不正确")
    private String idCard;

    /** 邮箱 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最长 100 字")
    private String email;

    /** 所属组织 ID（org 表主键，可空） */
    private Long orgId;

    /** 人员类型：STUDENT 学生 / TEACHER 教师 / OTHER 其他，不传默认 STUDENT */
    @Pattern(regexp = "STUDENT|TEACHER|OTHER", message = "人员类型取值不合法")
    private String memberType;

    /** 技能标签值列表（对应 SKILL 字典 item_value，整组提交） */
    private List<String> skills;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;
}
