package com.viomanager.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.user.entity.ForumUser;
import com.viomanager.user.mapper.ForumUserMapper;
import com.viomanager.user.service.IForumUserService;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 论坛用户快照服务实现。
 * 换名链路（方案 §二.3）：转发用户 JWT 调管理侧 GET /api/auth/my-profile，
 * 同一 JWT_SECRET 下管理侧验签自然通过；管理侧不可达时组织名按空降级不阻塞业务。
 */
@Slf4j
@Service
public class ForumUserServiceImpl implements IForumUserService {

    @Resource
    private ForumUserMapper forumUserMapper;

    /** 管理侧 my-profile 地址（application.yml vio.forum.profile-url 注入） */
    @Value("${vio.forum.profile-url}")
    private String profileUrl;

    /** 换名客户端：5 秒超时，管理侧不可达快速失败走降级 */
    private final RestClient restClient;

    public ForumUserServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public ForumUser touch(CurrentUser user, String authHeader) {
        ForumUser snapshot = forumUserMapper.selectOne(
                new QueryWrapper<ForumUser>().eq("user_id", user.getUserId()));
        //1.首次写操作：建快照，姓名冻结自 JWT，组织名问管理侧
        if (snapshot == null) {
            ForumUser fresh = new ForumUser()
                    .setId(IdWorker.getId())
                    .setUserId(user.getUserId())
                    .setRealName(user.getRealName())
                    .setOrgId(user.getOrgId())
                    .setOrgName(fetchOrgName(authHeader))
                    .setFirstSeenAt(LocalDateTime.now())
                    .setLastActiveAt(LocalDateTime.now());
            //并发首访（修复 M9，2026-10-16 实测复现后修复）：原子 upsert 单语句落快照。
            //  旧路"insert→撞 uk 报错→catch 后 FOR UPDATE 重查"实测死锁：MySQL 撞唯一键
            //  报错时给事务留共享锁，多个并发败者各持 S 锁再各自请求 X 锁（FOR UPDATE），
            //  S→X 升级互等成环，5 并发死锁 3 个（1000，日志见 DeadlockLoserDataAccessException）。
            //  upsert 无报错路径，败者转更新分支按行锁串行排队，不可能成环。
            forumUserMapper.upsertFirstSeen(fresh);
            //重查取真实行（胜者行含真实 id；本事务已持该行 X 锁，锁定读即时返回）。
            //  ⚠️ 必须锁定读：本方法常在回帖/发帖事务内被调用，MVCC 快照可能已建立，
            //  普通 SELECT 读不到对手刚提交的行，会空指针
            snapshot = forumUserMapper.selectOne(
                    new QueryWrapper<ForumUser>().eq("user_id", user.getUserId()).last("FOR UPDATE"));
        }
        //2.已存在：刷新活跃时间；realName 以 JWT 为准（管理侧改名后新帖带新名，旧帖维持写入时快照）；
        //  orgId 变化（用户调动）才重新换名，组织改名刻意不回刷（方案 §3.4）
        boolean nameChanged = user.getRealName() != null
                && !user.getRealName().equals(snapshot.getRealName());
        boolean orgChanged = !eq(user.getOrgId(), snapshot.getOrgId());
        snapshot.setLastActiveAt(LocalDateTime.now());
        if (nameChanged) {
            snapshot.setRealName(user.getRealName());
        }
        if (orgChanged) {
            //换名失败不落库（修复 M2 论坛侧）：先问管理侧，问到才一起更新 orgId+orgName；
            //  问不到保留旧值，orgChanged 下次仍为 true 自然重试——原实现先存 orgId 再取名，
            //  管理侧抖一下 org_name 就永久为 null 且再不触发换名
            String newName = fetchOrgName(authHeader);
            if (newName != null) {
                snapshot.setOrgId(user.getOrgId());
                snapshot.setOrgName(newName);
            }
        }
        forumUserMapper.updateById(snapshot);
        return snapshot;
    }

    @Override
    public long countMembers() {
        return forumUserMapper.selectCount(new QueryWrapper<>());
    }

    /**
     * 调管理侧 my-profile 换组织名；失败按 null 降级（快照的 org_name 允许为空，
     * 展示层前缀缺组织名只影响观感，不该把发帖/回帖打挂）。
     */
    private String fetchOrgName(String authHeader) {
        try {
            ProfileResponse resp = restClient.get()
                    .uri(profileUrl)
                    .header("Authorization", authHeader == null ? "" : authHeader)
                    .retrieve()
                    .body(ProfileResponse.class);
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                return resp.getData().getOrgName();
            }
            log.warn("my-profile 返回异常: code={}, message={}",
                    resp == null ? null : resp.getCode(), resp == null ? null : resp.getMessage());
        } catch (Exception e) {
            log.warn("管理侧不可达，org_name 降级为空: {}", e.getMessage());
        }
        return null;
    }

    /** Long 等值比较，null 安全 */
    private boolean eq(Long a, Long b) {
        if (a == null && b == null) {
            return true;
        }
        return a != null && a.equals(b);
    }

    /** 管理侧统一响应壳 */
    @Data
    static class ProfileResponse {
        private int code;
        private String message;
        private ProfileData data;
    }

    /** my-profile 的 data 载荷（只取关心的字段） */
    @Data
    static class ProfileData {
        private Long userId;
        private String realName;
        private String role;
        private Long orgId;
        private String orgName;
    }
}
