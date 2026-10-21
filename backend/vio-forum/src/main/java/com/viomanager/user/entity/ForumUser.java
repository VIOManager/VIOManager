package com.viomanager.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 论坛用户快照，对应表 forum_user（memberCount 数据源 + author_group 冻结来源）。
 * 不继承 BaseEntity：本表没有 deleted / 审计四件套，主键与时间列自行声明。
 * 生命周期（方案 §3.4）：首次写操作触发 insert；写操作时 JWT orgId 与快照不一致
 * 才调管理侧 my-profile 换名刷新；org_name 展示写入时的组织名（组织改名刻意不回刷）。
 */
@Data
@Accessors(chain = true)
@TableName("forum_user")
public class ForumUser {

    /** 雪花 ID（本表不继承 BaseEntity，须显式 ASSIGN_ID，默认 AUTO 会插入报错） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** sys_user.id（JWT 载荷，跨库逻辑引用） */
    private Long userId;

    /** 姓名快照（首见时从 JWT realName 冻结） */
    private String realName;

    /** 组织 ID 快照（与 JWT 比对，变化则触发换名） */
    private Long orgId;

    /** 组织名快照（写入时冻结；经管理侧 my-profile 换名） */
    private String orgName;

    /** 首次写操作时间（DB 默认值兜底，插入时可不设） */
    private LocalDateTime firstSeenAt;

    /** 最后写操作时间 */
    private LocalDateTime lastActiveAt;
}
