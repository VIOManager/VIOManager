package com.viomanager.team.handler;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.approval.handler.ApprovalHandler;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.team.entity.TeamMember;
import com.viomanager.team.mapper.TeamMemberMapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 移除队员审批回调（TEAM_REMOVE_MEMBER）：批准后置 team_member status=REMOVED。
 * 回调在审批处理同一事务内，数据库操作失败会连同审批结果一起回滚。
 */
@Slf4j
@Component
public class TeamRemoveMemberHandler implements ApprovalHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Override
    public String bizType() {
        return "TEAM_REMOVE_MEMBER";
    }

    @Override
    public Long onApproved(Long approvalId, Long bizId, String payloadJson) {
        Map<String, Object> p = parsePayload(payloadJson);
        Long teamId = toLong(p.get("teamId"));
        Long memberId = toLong(p.get("memberId"));
        // 置 REMOVED + 记录离开时间与原因
        UpdateWrapper<TeamMember> uw = new UpdateWrapper<>();
        uw.eq("team_id", teamId).eq("member_id", memberId)
                .in("status", "UNCONFIRMED", "CONFIRMED").eq("deleted", 0)
                .set("status", "REMOVED")
                .set("left_at", LocalDateTime.now())
                .set("left_reason", "审批移除");
        if (teamMemberMapper.update(null, uw) == 0) {
            log.warn("TEAM_REMOVE_MEMBER 回调未命中有效记录: teamId={}, memberId={}", teamId, memberId);
        }
        return null;
    }

    @Override
    public void onRejected(Long approvalId, Long bizId, String payloadJson) {
        // 驳回无需动作（成员保持原状态）
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BizException(TeamErrorCode.CONTEST_STATE_NOT_ALLOWED, "审批快照解析失败");
        }
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
