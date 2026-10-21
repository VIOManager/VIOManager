package com.viomanager.approval.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.approval.entity.Approval;
import com.viomanager.approval.entity.ApprovalRecord;
import com.viomanager.approval.mapper.ApprovalMapper;
import com.viomanager.approval.mapper.ApprovalRecordMapper;
import com.viomanager.approval.support.ApprovalOutboxSupport;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审批过期兜底任务（插入点④）：主路径是 MQ 延迟消息到点触发（ApprovalExpireListener），
 * 本任务降频为 10 分钟当对账兜底——MQ 全挂时过期仍会被兜住，最多晚十分钟。
 * 方案约定不提供 HTTP 过期接口——过期只由时间驱动，防止绕过审批窗口。
 * 条件更新防并发：同一张单不会被任务/消费者/管理员中的两方同时处理两次。
 */
@Slf4j
@Component
public class ApprovalExpireTask {

    @Resource
    private ApprovalMapper approvalMapper;

    @Resource
    private ApprovalRecordMapper approvalRecordMapper;

    @Resource
    private ApprovalOutboxSupport approvalOutboxSupport;

    /**
     * 兜底对账，10 分钟一次（原为每分钟全表扫——延迟消息上线后的历史使命结束）。
     * 注意 10 分钟粒度同时保证了 MQ 端到端测试的确定性：20 秒窗口内本任务不可能触发。
     */
    @Scheduled(fixedDelay = 600000)
    public void scheduledExpire() {
        int expired = expireOverdue();
        if (expired > 0) {
            log.info("审批过期兜底任务：本次转 EXPIRED {} 张", expired);
        }
    }

    /**
     * 把到期未处理的单转 EXPIRED 并记 EXPIRE 流水，返回处理张数（测试直接调它）。
     * 逐张走 expireOne——兜底轮询与延迟消息消费者共用同一段单条逻辑，两个触发源行为必然一致。
     */
    @Transactional
    public int expireOverdue() {
        //1.捞出到期未处理的单子——只取 id：循环里只用它，SELECT * 会白拉整行
        //  （含 biz_payload 大 JSON 列），兜底场景积压越多省得越多
        List<Approval> overdue = approvalMapper.selectList(new QueryWrapper<Approval>()
                .select("id")
                .eq("status", "PENDING").lt("expire_at", LocalDateTime.now()));
        int count = 0;
        for (Approval approval : overdue) {
            //2.单张过期
            if (expireOne(approval.getId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 单张过期：条件更新 PENDING→EXPIRED + EXPIRE 流水，返回是否真的转了。
     * 0 行 = 已被管理员处理 / 早已过期（延迟消息与人工处理的竞态，正常分支）。
     */
    @Transactional
    public boolean expireOne(Long approvalId) {
        //1.条件更新（防并发双批）：WHERE status='PENDING'，与管理员处理互斥
        UpdateWrapper<Approval> update = new UpdateWrapper<>();
        update.eq("id", approvalId).eq("status", "PENDING")
                .set("status", "EXPIRED")
                .set("handled_at", LocalDateTime.now());
        int rows = approvalMapper.update(null, update);
        if (rows == 0) {
            return false;
        }
        //2.记 EXPIRE 流水：系统动作，操作人为空
        ApprovalRecord record = new ApprovalRecord();
        record.setApprovalId(approvalId);
        record.setAction("EXPIRE");
        record.setOperatorRole(null);
        record.setFromStatus("PENDING");
        record.setToStatus("EXPIRED");
        record.setCreatedAt(LocalDateTime.now());
        approvalRecordMapper.insert(record);
        //3.同事务写本地消息表（插入点③）：过期也是状态变更，目标人的红点要刷新。
        //   条件更新刚成功，读回快照必非空；防御性判空避免极端情况下过期循环卡死
        Approval snapshot = approvalMapper.selectById(approvalId);
        if (snapshot != null) {
            approvalOutboxSupport.recordStatusChange(snapshot, "EXPIRE", "EXPIRED");
        }
        return true;
    }
}
