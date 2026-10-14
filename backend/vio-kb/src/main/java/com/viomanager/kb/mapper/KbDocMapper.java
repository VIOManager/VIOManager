package com.viomanager.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.kb.entity.KbDoc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 知识库文档 Mapper。
 *
 * @DataScope 声明为 ALL 的理由：PUBLISHED 文档是全员可见的公共数据（清单拍板：学生可见性=
 * 全部 PUBLISHED，不按组织隔离）。⚠️ 不声明的话拦截器对学生默认拒绝（防泄露底线），
 * 学生浏览 KB01 会全挂——这是本服务最容易踩的坑，删注解前先想清楚。
 * 管理端范围（KB17 部门管理员看本部门）在 service 显式控制，不走拦截器注入
 * （独立库无 sys_org，ORG 模式的子树注入查不了表）。
 *
 * 计数与状态方法全部是原子/条件 UPDATE（WHERE 带旧值），并发安全不依赖 Java 锁。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface KbDocMapper extends BaseMapper<KbDoc> {

    /** 浏览数原子 +1（KB02 详情页同步调用；200 人规模直更库，阶段 3 再议 Redis 计数） */
    @Update("UPDATE kb_doc SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int increaseViewCount(@Param("id") Long id);

    /** 收藏数原子 ±delta（与 kb_collect 增删同事务调用） */
    @Update("UPDATE kb_doc SET collect_count = collect_count + #{delta} WHERE id = #{id} AND deleted = 0")
    int adjustCollectCount(@Param("id") Long id, @Param("delta") int delta);

    /** 评论数原子 ±delta（口径=可见数，发/删/隐藏/恢复随口径调用） */
    @Update("UPDATE kb_doc SET comment_count = comment_count + #{delta} WHERE id = #{id} AND deleted = 0")
    int adjustCommentCount(@Param("id") Long id, @Param("delta") int delta);

    /** 置顶原子翻转（KB15：两个管理员同时点也不会翻乱，单条 SQL 天然串行） */
    @Update("UPDATE kb_doc SET top_flag = 1 - top_flag, updated_by = #{operator} "
            + "WHERE id = #{id} AND deleted = 0")
    int toggleTop(@Param("id") Long id, @Param("operator") Long operator);

    /**
     * 状态条件流转（KB16）：WHERE 带旧状态，0 行命中=状态已被并发改变。
     * updated_at 由列上 ON UPDATE CURRENT_TIMESTAMP 自动刷新，不手工 SET。
     */
    @Update("UPDATE kb_doc SET status = #{to}, updated_by = #{operator} "
            + "WHERE id = #{id} AND status = #{from} AND deleted = 0")
    int casStatus(@Param("id") Long id, @Param("from") String from,
                  @Param("to") String to, @Param("operator") Long operator);
}
