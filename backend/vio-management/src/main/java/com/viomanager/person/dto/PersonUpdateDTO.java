package com.viomanager.person.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 编辑人员入参（P02）：管理员修改已有人员档案时前端提交的数据。
 * 必须携带人员 id；其余字段可空——为 null 表示"不改这一项"（部分更新，见 service）。
 * 刻意不带学号 studentNo：建表注释明确"学号不可修改"，档案标识字段不开放编辑；
 * 身份证号传了就重新加密覆盖，不传保持原值。
 */
@Data
public class PersonUpdateDTO {

    /** 人员 ID（必填） */
    @NotNull(message = "人员 id 不能为空")
    private Long id;

    /** 姓名 */
    @Size(max = 50, message = "姓名最长 50 字")
    private String name;

    /** 学工号（可修改，修改后仍需全局唯一） */
    @Size(max = 32, message = "学工号最长 32 字")
    private String jobNo;

    /** 手机号（⚠️ 敏感字段：非超管出参脱敏） */
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

    /** 性别：MALE / FEMALE / UNKNOWN */
    @Pattern(regexp = "MALE|FEMALE|UNKNOWN", message = "性别取值不合法")
    private String gender;

    /** 民族 */
    @Size(max = 32, message = "民族最长 32 字")
    private String nation;

    /** 身份证号（⚠️ 敏感字段：传了则服务端重新加密覆盖） */
    @Pattern(regexp = "\\d{17}[0-9Xx]", message = "身份证号格式不正确")
    private String idCard;

    /** 邮箱 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最长 100 字")
    private String email;

    /** 所属组织 ID */
    private Long orgId;

    /** 人员类型：STUDENT / TEACHER / OTHER */
    @Pattern(regexp = "STUDENT|TEACHER|OTHER", message = "人员类型取值不合法")
    private String memberType;

    /** 人员状态：1 在册 0 离册 */
    @Min(value = 0, message = "人员状态取值不合法")
    @Max(value = 1, message = "人员状态取值不合法")
    private Integer status;

    /** 技能标签值列表（整组替换；null = 不改，空列表 = 清空） */
    private List<String> skills;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;
}
