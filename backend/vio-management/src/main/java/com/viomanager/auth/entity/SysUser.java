package com.viomanager.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 登录账号实体，对应表 sys_user。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /**
     * 关联人员档案 ID；纯管理账号可为空
     */
    private Long memberId;

    /**
     * 登录名，默认 = 学工号，可修改
     */
    private String loginName;

    /**
     * 密码，BCrypt 加密存储
     */
    private String password;

    /**
     * 角色：SUPER_ADMIN / DEPT_ADMIN / STUDENT
     */
    private String role;

    /**
     * 所属组织 ID，部门管理员的数据范围根节点
     */
    private Long orgId;

    /**
     * 姓名冗余，避免每次展示都关联 member
     */
    private String realName;

    /**
     * 是否仍为初始密码：1 是（前端弹改密提示）0 否
     */
    private Integer defaultPassword;

    /**
     * 状态：1 启用 0 停用
     */
    private Integer status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 最后登录 IP
     */
    private String lastLoginIp;

    /**
     * 密码最后修改时间，改密踢人的比对依据
     */
    private LocalDateTime pwdUpdatedAt;

    /**
     * 备注
     */
    private String remark;
}
