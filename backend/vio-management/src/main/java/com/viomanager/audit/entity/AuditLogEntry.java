package com.viomanager.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 审计日志读侧实体（表 audit_log）。
 * 刻意不继承 BaseEntity：该表是只增不改的日志表，没有 deleted / updated_at 基座尾列
 * （写入侧 AuditLogDao 裸 JDBC 同款结论）；读侧只查不改，无逻辑删除语义。
 */
@Data
@Accessors(chain = true)
@TableName("audit_log")
public class AuditLogEntry {

    /** 主键（雪花 ID，写入侧切面生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作人用户 ID，匿名场景为空 */
    private Long userId;

    /** 操作人登录名 */
    private String loginName;

    /** 操作人姓名 */
    private String realName;

    /** 操作人所属组织，部门管理员按此过滤 */
    private Long orgId;

    /** 操作人角色 */
    private String role;

    /** 客户端 IP（已解析代理头） */
    private String ip;

    /** 浏览器标识 */
    private String userAgent;

    /** 请求方法 */
    private String httpMethod;

    /** 请求路径 */
    private String uri;

    /** 业务类型代码 */
    private String bizType;

    /** 业务类型中文名 */
    private String bizTypeLabel;

    /** 操作摘要 */
    private String summary;

    /** 请求参数摘要，敏感接口为空 */
    private String requestParams;

    /** 是否成功：1 成功 0 失败（Integer 直出数字，前端按 1/0 判断） */
    private Integer success;

    /** 失败原因 */
    private String errorMessage;

    /** 耗时毫秒 */
    private Long costMillis;

    /** 发生时间 */
    private LocalDateTime createdAt;
}
