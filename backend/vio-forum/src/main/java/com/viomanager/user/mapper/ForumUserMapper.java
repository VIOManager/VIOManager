package com.viomanager.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.user.entity.ForumUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 论坛用户快照 mapper。
 */
@Mapper
public interface ForumUserMapper extends BaseMapper<ForumUser> {

    /**
     * 首访快照原子 upsert（并发修复 M9）：并发首访同一 user_id 时，胜者插入、
     * 败者在单语句内直接转更新（last_active_at 刷新），按行锁串行排队。
     * 相比"insert 撞唯一键报错后再重查"，无报错路径也就无锁升级，不会死锁。
     * ⚠️ 自定义 SQL 不走 MP 的 ASSIGN_ID 自动填充，调用方须先 setId(IdWorker.getId())。
     */
    @Insert("INSERT INTO forum_user (id, user_id, real_name, org_id, org_name, first_seen_at, last_active_at) "
            + "VALUES (#{id}, #{userId}, #{realName}, #{orgId}, #{orgName}, #{firstSeenAt}, #{lastActiveAt}) "
            + "ON DUPLICATE KEY UPDATE last_active_at = NOW()")
    int upsertFirstSeen(ForumUser user);
}
