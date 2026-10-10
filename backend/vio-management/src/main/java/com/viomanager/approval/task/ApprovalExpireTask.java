package com.viomanager.approval.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.approval.entity.Approval;
import com.viomanager.approval.entity.ApprovalRecord;
import com.viomanager.approval.mapper.ApprovalMapper;
import com.viomanager.approval.mapper.ApprovalRecordMapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审批过期定时任务：每分钟扫一遍 PENDING 且 expire_at 已过的单子转 EXPIRED。
 * 方案约定不提供 HTTP 过期接口——过期只由时间驱动，防止绕过审批窗口。
 * 条件更新防并发：同一张单不会被任务和管理员同时处理两次。
 */
@Slf4j
@Component
public class ApprovalExpireTask {

    @Resource
    private ApprovalMapper approvalMapper;

    @Resource
    private ApprovalRecordMapper approvalRecordMapper;

    /** 每分钟一次（7 天时效的单子，分钟级扫描绰绰有余） */
    @Scheduled(fixedDelay = 60000)
    public void scheduledExpire() {
        int expired = expireOverdue();
        if (expired > 0) {
            log.info("审批过期任务：本次转 EXPIRED {} 张", expired);
        }
    }

    /** 把到期的 PENDING 单转 EXPIRED 并记 EXPIRE 流水，返回处理张数（测试直接调它） */
    @Transactional
    public int expireOverdue() {
        //1.捞出到期未处理的单子
        List<Approval> overdue = approvalMapper.selectList(new QueryWrapper<Approval>()
                .eq("status", "PENDING").lt("expire_at", LocalDateTime.now()));
        int count = 0;
        for (Approval approval : overdue) {
            //2.条件更新转 EXPIRED：0 行说明刚被管理员处理了，跳过
            UpdateWrapper<Approval> update = new UpdateWrapper<>();
            update.eq("id", approval.getId()).eq("status", "PENDING")
                    .set("status", "EXPIRED")
                    .set("handled_at", LocalDateTime.now());
            int rows = approvalMapper.update(null, update);
            if (rows == 0) {
                continue;
            }
            //3.记 EXPIRE 流水：系统动作，操作人为空
            ApprovalRecord record = new ApprovalRecord();
            record.setApprovalId(approval.getId());
            record.setAction("EXPIRE");
            record.setOperatorRole(null);
            record.setFromStatus("PENDING");
            record.setToStatus("EXPIRED");
            record.setCreatedAt(LocalDateTime.now());
            approvalRecordMapper.insert(record);
            count++;
        }
        return count;
    }
}
