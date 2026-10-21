package com.viomanager.board.entity;

import com.viomanager.common.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 论坛版块实体，对应表 forum_board。
 * 状态机：NORMAL 正常 / DISABLED 停用（停用后禁发新帖，旧帖可见）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("forum_board")
public class ForumBoard extends BaseEntity {

    /** 版块编码：GENERAL/EE/MODELING/AI（前端路由与筛选用） */
    private String code;

    /** 版块名 */
    private String name;

    /** 版块简介 */
    private String description;

    /** 排序号，小的在前 */
    private Integer sortOrder;

    /** 状态：NORMAL 正常 / DISABLED 停用 */
    private String status;
}
