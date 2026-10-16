package com.viomanager.person.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 人员档案出参（P01 新增返回 / P04 列表 / P05 详情）：给前端展示的数据模型，
 * 不直接返回 entity，防止数据库敏感字段泄露。
 * ⚠️ 权限脱敏与 service 配合（PersonServiceImpl.toVO）：
 *  - 超管（SUPER_ADMIN）：phone 返回完整值，idCard 返回解密明文；
 *  - 其他角色：phone 中间 4 位打星（138****5678），idCard 恒为 null，只给 idCardLast4 尾号展示。
 */
@Data
public class PersonVO {

    /** 人员 ID（Jackson 全局把 Long 序列化为字符串） */
    private Long id;

    /** 姓名 */
    private String name;

    /** 学号 */
    private String studentNo;

    /** 学工号 */
    private String jobNo;

    /** 学院 */
    private String college;

    /** 专业 */
    private String major;

    /** 班级 */
    private String className;

    /** 性别：MALE / FEMALE / UNKNOWN */
    private String gender;

    /** 民族 */
    private String nation;

    /** 手机号（⚠️ 按角色脱敏：非超管 138****5678） */
    private String phone;

    /** 身份证号（⚠️ 仅超管返回解密明文；其他角色恒为 null） */
    private String idCard;

    /** 身份证号后 4 位（所有人可见的脱敏展示位） */
    private String idCardLast4;

    /** 邮箱 */
    private String email;

    /** 证件照文件 ID（前端拿它调 /api/files/{fileId}/download） */
    private Long photoFileId;

    /** 电子签名文件 ID */
    private Long signatureFileId;

    /** 所属组织 ID */
    private Long orgId;

    /** 人员类型：STUDENT / TEACHER / OTHER */
    private String memberType;

    /** 人员状态：1 在册 0 离册 */
    private Integer status;

    /** 是否已绑定登录账号：1 是 0 否 */
    private Integer hasAccount;

    /** 技能标签值列表（SKILL 字典 item_value，展示文案用 /api/dicts/SKILL 映射） */
    private List<String> skills;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
