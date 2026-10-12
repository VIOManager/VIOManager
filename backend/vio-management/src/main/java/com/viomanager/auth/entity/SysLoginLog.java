package com.viomanager.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 登录日志实体，对应表 sys_login_log。
 * 注意：本表只有 created_at 一个审计列，没有 deleted / updated —— 不能继承 BaseEntity。
 */
@Data
@Accessors(chain = true)
@TableName("sys_login_log")
public class SysLoginLog {

    /**
     * 主键（雪花 ID）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户 ID，登录名不存在时为空
     */
    private Long userId;

    /**
     * 尝试登录的登录名
     */
    private String loginName;

    /**
     * 登录方式：PASSWORD 账密
     */
    private String loginType;

    /**
     * 是否成功：1 成功 0 失败
     */
    private Integer success;

    /**
     * 失败原因
     */
    private String failReason;

    /**
     * 客户端 IP
     */
    private String ip;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 发生时间，由 AuditMetaObjectHandler 自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
