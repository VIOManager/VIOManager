package com.viomanager.approval.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.approval.entity.Approval;
import com.viomanager.approval.mapper.ApprovalMapper;
import com.viomanager.approval.vo.UnreadCountVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 红点计数的唯一口径（P08 两条 selectCount 抽出）。
 * 三个消费方共用同一段逻辑，保证写路径（消费者重算回写 Redis）与读路径
 * （miss 回填、10 分钟对账）算出来的数永远一致——重算天然幂等、不会漂移。
 * ApprovalMapper 是 SELF 声明：定时/消费线程无登录态拦截器放行，HTTP 线程 SELF 不注入，
 * 两种线程拿到的是同一份无过滤数据。
 */
@Component
public class UnreadCounter {

    @Resource
    private ApprovalMapper approvalMapper;

    /** 按人员档案 ID 重算两类未读数 */
    public UnreadCountVO countFor(Long memberId) {
        //1.待处理数：发给我的邀请 / 解散通知（PENDING 且未读）
        Long todoCount = approvalMapper.selectCount(new QueryWrapper<Approval>()
                .eq("target_member_id", memberId).eq("status", "PENDING").eq("target_read", 0));
        //2.结果数：我发起的申请已出结果但我没看
        Long resultCount = approvalMapper.selectCount(new QueryWrapper<Approval>()
                .eq("applicant_id", memberId).ne("status", "PENDING").eq("applicant_read", 0));
        UnreadCountVO vo = new UnreadCountVO();
        vo.setTodoCount(todoCount == null ? 0 : todoCount.intValue());
        vo.setResultCount(resultCount == null ? 0 : resultCount.intValue());
        return vo;
    }
}
