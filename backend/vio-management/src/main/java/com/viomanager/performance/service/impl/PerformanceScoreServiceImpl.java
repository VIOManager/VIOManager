package com.viomanager.performance.service.impl;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.PerformanceErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.performance.dto.WeeklyScoreDTO;
import com.viomanager.performance.entity.PerformanceRule;
import com.viomanager.performance.entity.PerformanceScore;
import com.viomanager.performance.entity.PerformanceWeekly;
import com.viomanager.performance.mapper.PerformanceWeeklyMapper;
import com.viomanager.performance.service.PerformanceScoreService;
import com.viomanager.performance.support.PerfCalcSupport;
import com.viomanager.schedule.support.SubOrgSupport;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 打分实现。链路（接口清单 PF07）：状态校验（8042）→ 规则匹配（8046/8043/8044）
 * → 快照落库 + 加权总分 → 事务内重算汇总、排名与标签（PerfCalcSupport）。
 */
@Service
public class PerformanceScoreServiceImpl implements PerformanceScoreService {

    @Resource
    private PerformanceWeeklyMapper weeklyMapper;

    @Resource
    private PerfCalcSupport calcSupport;

    @Resource
    private SubOrgSupport subOrgSupport;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void score(Long id, WeeklyScoreDTO dto) {
        CurrentUser user = UserHolder.getUser();
        PerformanceWeekly weekly = requireVisibleWeekly(id);
        if (!"SUBMITTED".equals(weekly.getStatus())) {
            throw new BizException(PerformanceErrorCode.WEEKLY_STATUS_NOT_ALLOWED, "仅待审核周报可打分");
        }
        //1.生效规则（特定学期覆盖默认），enabled 维度即打分维度集合
        Map<String, PerformanceRule> ruleMap = calcSupport.effectiveRuleMap(
                weekly.getSubOrgId(), weekly.getSemesterId());
        List<PerformanceRule> enabledRules = ruleMap.values().stream()
                .filter(rule -> rule.getEnabled() != null && rule.getEnabled() == 1)
                .toList();
        if (enabledRules.isEmpty()) {
            throw new BizException(PerformanceErrorCode.RULE_NOT_CONFIGURED);
        }
        Set<String> ruleKeys = new HashSet<>();
        enabledRules.forEach(rule -> ruleKeys.add(rule.getDimensionKey()));
        Set<String> scoreKeys = new HashSet<>();
        dto.getScores().forEach(line -> scoreKeys.add(line.getDimensionKey()));
        if (!ruleKeys.equals(scoreKeys)) {
            throw new BizException(PerformanceErrorCode.DIMENSION_MISMATCH,
                    "打分维度须与该子组织启用规则完全一致：" + ruleKeys);
        }
        //2.逐维度校验满分（8044）并组装得分行
        List<PerformanceScore> lines = dto.getScores().stream().map(line -> {
            PerformanceRule rule = ruleMap.get(line.getDimensionKey());
            if (line.getScore().compareTo(rule.getMaxScore()) > 0) {
                throw new BizException(PerformanceErrorCode.SCORE_EXCEED_MAX,
                        "维度「" + rule.getDimensionName() + "」得分 " + line.getScore()
                                + " 超过满分 " + rule.getMaxScore());
            }
            PerformanceScore scoreLine = new PerformanceScore();
            scoreLine.setDimensionKey(line.getDimensionKey());
            scoreLine.setScore(line.getScore());
            scoreLine.setCommentText(line.getCommentText());
            return scoreLine;
        }).toList();
        //3.快照落库 + 加权总分（快照 weight/max_score 在 persistScores 内取当时规则）
        BigDecimal total = calcSupport.persistScores(weekly, ruleMap, lines);
        //4.周报转 SCORED，随后事务内重算汇总/排名/标签
        weekly.setStatus("SCORED")
                .setTotalScore(total)
                .setScoredAt(LocalDateTime.now())
                .setScoredBy(user.getUserId())
                .setScoredByName(user.getRealName() != null ? user.getRealName() : user.getLoginName())
                .setScoreComment(dto.getScoreComment());
        weeklyMapper.updateById(weekly);
        calcSupport.recalcSummaryAndRank(weekly);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        PerformanceWeekly weekly = requireVisibleWeekly(id);
        if (!"SUBMITTED".equals(weekly.getStatus())) {
            throw new BizException(PerformanceErrorCode.WEEKLY_STATUS_NOT_ALLOWED, "仅待审核周报可驳回");
        }
        weekly.setStatus("REJECTED").setRejectReason(reason);
        weeklyMapper.updateById(weekly);
    }

    /** 加载周报并做管理端可见性校验（部门管理员本子树；学生不可调——controller 已挡）。 */
    private PerformanceWeekly requireVisibleWeekly(Long id) {
        PerformanceWeekly weekly = weeklyMapper.selectById(id);
        if (weekly == null) {
            throw new BizException(PerformanceErrorCode.WEEKLY_NOT_FOUND);
        }
        List<Long> visible = subOrgSupport.visibleOrgIds(UserHolder.getUser());
        if (visible != null && !visible.contains(weekly.getOrgId())) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权操作该子组织的周报");
        }
        return weekly;
    }
}
