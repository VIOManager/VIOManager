package com.viomanager.person.dto;

import lombok.Data;

/**
 * 人员分页查询入参（M01 列表 / export 导出共用）：管理员后台列表页筛选条件。
 * 字段口径对齐 A 线契约 M01 与前端 member.ts 的 MemberQuery：
 * keyword 单搜（姓名/学号/学院/专业）、orgId/memberType 精确、skill 按技能标签、
 * hasAccount 前端传 true/false 字符串，服务层归一化为 1/0。
 * page/size 遵循规范 §2.3（默认 1/20，size 上限 100 服务层截断）。
 */
@Data
public class PersonQueryDTO {

    /** 关键字（模糊搜索：姓名 / 学号 / 手机号，对齐契约 M01 与前端 mock） */
    private String keyword;

    /** 所属组织 ID（精确筛选；部门管理员会被服务端再叠加子树过滤） */
    private Long orgId;

    /** 人员类型：STUDENT / TEACHER / OTHER（精确筛选） */
    private String memberType;

    /** 技能标签值（SKILL 字典 item_value，筛"有这个标签"的人） */
    private String skill;

    /** 是否已绑定账号：true / false（前端字符串，服务层归一化） */
    private String hasAccount;

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数，默认 20，上限 100 */
    private Integer size = 20;
}
