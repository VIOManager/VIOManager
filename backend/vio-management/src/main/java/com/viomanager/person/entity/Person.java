package com.viomanager.person.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 人员档案实体，对应表 member（建表脚本 V1__init_52_tables.sql，表名沿用骨架的 member）。
 * 只做数据库映射：表有什么列这里就有什么属性，不写业务逻辑、不挂接口、不做校验；
 * 前后端传输一律走 dto 包的 DTO/VO，禁止把本实体直接返回给前端。
 * ⚠️ 敏感字段标记（加密/脱敏在 service 层做，见 PersonSensitiveSupport）：
 *  - idCard：身份证号，数据库只存 AES 密文，出参只对超管解密返回；
 *  - phone：手机号，FULL/CAPTAIN 视角出完整号（M02 四档裁剪见 PersonQuerySupport.toVO）。
 *  - idCardLast4：身份证尾号 4 位，专供列表脱敏展示，避免解密。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("member")
public class Person extends BaseEntity {

    /**
     * 姓名（必填）
     */
    private String name;

    /**
     * 学号。可空、不可重复、创建后不可修改（建表唯一键 uk_member_student_no）
     */
    private String studentNo;

    /**
     * 学工号。与学号相互独立，登录名默认取此值（建表唯一键 uk_member_job_no）
     */
    private String jobNo;

    /**
     * 手机号 ⚠️ 敏感字段：出参按 M02 视角裁剪（LIMITED 档不返回，FULL/CAPTAIN 档完整）
     */
    private String phone;

    /**
     * 学院
     */
    private String college;

    /**
     * 专业
     */
    private String major;

    /**
     * 班级
     */
    private String className;

    /**
     * 性别：MALE 男 / FEMALE 女 / UNKNOWN 未知
     */
    private String gender;

    /**
     * 民族
     */
    private String nation;

    /**
     * 身份证号密文（AES/GCM，Base64）⚠️ 敏感字段：禁止明文入库；解密只在超管查看详情/导出时进行
     */
    private String idCard;

    /**
     * 身份证号后 4 位，供列表脱敏展示，避免解密
     */
    private String idCardLast4;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 证件照，引用 file_object.id（照片本体存 MinIO，由文件模块管理）
     */
    private Long photoFileId;

    /**
     * 电子签名，引用 file_object.id
     */
    private Long signatureFileId;

    /**
     * 所属组织（分子组织）ID，引用 org.id
     */
    private Long orgId;

    /**
     * 人员类型：STUDENT 学生 / TEACHER 教师 / OTHER 其他
     */
    private String memberType;

    /**
     * 人员状态：1 在册 0 离册
     */
    private Integer status;

    /**
     * 是否已绑定登录账号：1 是 0 否（账号在 sys_user，由认证模块管理）
     */
    private Integer hasAccount;

    /**
     * 备注
     */
    private String remark;
}
