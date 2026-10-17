package com.viomanager.person.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 人员档案出参（M02 详情 / M01 列表项 / M03 新建返回）。
 * 不直接返回 entity，防止数据库敏感字段泄露。
 * ⚠️ 出参按 PersonViewerSupport 判定的视角裁剪（A 线契约 M02 四档）：
 *  - FULL 全量：超管 / 本部门管理员 / 本人——phone 完整、idCard 解密明文、附 loginName；
 *  - CAPTAIN 队长档：本队队长——phone 完整、idCard 恒 null（完整号走 M10）、其余同全量；
 *  - LIMITED 受限档：其他人——仅 name/college/className/memberType/orgName/skills + limited=true，
 *    其余字段全 null（含手机号、学号、邮箱）。
 * 列表（M01）契约规定永不返回 idCard/photoFileId/email，与视角裁剪叠加生效。
 */
@Data
public class PersonVO {

    /** 人员 ID（Jackson 全局把 Long 序列化为字符串） */
    private Long id;

    /** 姓名 */
    private String name;

    /** 学号（LIMITED 档为 null） */
    private String studentNo;

    /** 学工号（LIMITED 档为 null） */
    private String jobNo;

    /** 学院 */
    private String college;

    /** 专业（LIMITED 档为 null） */
    private String major;

    /** 班级 */
    private String className;

    /** 性别：MALE / FEMALE / UNKNOWN（LIMITED 档为 null） */
    private String gender;

    /** 民族（LIMITED 档为 null） */
    private String nation;

    /** 手机号（⚠️ FULL/CAPTAIN 档完整；LIMITED 档为 null） */
    private String phone;

    /** 身份证号明文（⚠️ 仅 FULL 档返回；CAPTAIN/LIMITED 恒 null，完整号走 M10 且写审计） */
    private String idCard;

    /** 身份证号后 4 位（FULL/CAPTAIN 档可见的脱敏展示位） */
    private String idCardLast4;

    /** 邮箱（LIMITED 档为 null；列表 M01 恒 null） */
    private String email;

    /** 证件照文件 ID（列表 M01 恒 null；下载走 M11） */
    private Long photoFileId;

    /** 电子签名文件 ID（列表 M01 恒 null；下载走 M11） */
    private Long signatureFileId;

    /** 所属组织 ID（LIMITED 档为 null） */
    private Long orgId;

    /** 所属组织名称（所有档可见，前端列表展示用） */
    private String orgName;

    /** 人员类型：STUDENT / TEACHER / OTHER */
    private String memberType;

    /** 人员状态：1 在册 0 离册（LIMITED 档为 null） */
    private Integer status;

    /** 是否已绑定登录账号：1 是 0 否（LIMITED 档为 null） */
    private Integer hasAccount;

    /** 已绑定账号的登录名（⚠️ 仅 FULL 档返回，M13 绑定后前端展示用） */
    private String loginName;

    /** 技能标签值列表（SKILL 字典 item_value，展示文案用 /api/dicts/SKILL 映射） */
    private List<String> skills;

    /** 备注（LIMITED 档为 null；列表 M01 恒 null） */
    private String remark;

    /** 创建时间（LIMITED 档为 null） */
    private LocalDateTime createdAt;

    /** 更新时间（LIMITED 档为 null） */
    private LocalDateTime updatedAt;

    /** true = 受限视角（前端据此提示"无权查看完整档案"） */
    private Boolean limited;
}
