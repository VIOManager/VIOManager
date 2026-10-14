package com.viomanager.performance.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.viomanager.performance.entity.PerformanceRule;
import com.viomanager.performance.entity.PerformanceScore;
import com.viomanager.performance.entity.PerformanceSummary;
import com.viomanager.performance.entity.PerformanceWeekly;
import com.viomanager.performance.mapper.PerformanceRuleMapper;
import com.viomanager.performance.mapper.PerformanceScoreMapper;
import com.viomanager.performance.mapper.PerformanceSummaryMapper;
import com.viomanager.performance.mapper.PerformanceWeeklyMapper;
import com.viomanager.schedule.support.SubOrgSupport;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 绩效计分引擎（PF07 事务内调用）：
 * ① 规则匹配（特定学期优先、回退默认，与排班规则同口径）② 加权计分与快照落库
 * ③ 学期汇总重算（提交数/打分数/总分/平均/最高周/近 4 周趋势斜率）
 * ④ 子组织内排名重排（totalScore 降序、同分并列取先）
 * ⑤ 标签生成（活跃度上升/下降、功劳度高、连续贡献、高效周——阈值见接口清单 §4，文案可调）。
 */
@Component
public class PerfCalcSupport {

    /** 功劳度高：子组织内排名前 20%（接口清单 §4 默认阈值，待确认可调）。 */
    private static final BigDecimal TOP_RATIO = new BigDecimal("0.2");
    /** 高效周：单周加权总分 ≥ 该周满分（Σ max_score×weight 快照）的 90%。 */
    private static final BigDecimal EFFICIENT_RATIO = new BigDecimal("0.9");
    /** 连续贡献：连续打分周数下限。 */
    private static final int CONSECUTIVE_WEEKS = 4;

    @Resource
    private PerformanceRuleMapper ruleMapper;

    @Resource
    private PerformanceScoreMapper scoreMapper;

    @Resource
    private PerformanceSummaryMapper summaryMapper;

    @Resource
    private PerformanceWeeklyMapper weeklyMapper;

    @Resource
    private SubOrgSupport subOrgSupport;

    /** 生效规则视图：特定学期覆盖默认（semester_id 空），键 = dimensionKey。 */
    public Map<String, PerformanceRule> effectiveRuleMap(Long subOrgId, Long semesterId) {
        subOrgSupport.requireSubOrg(subOrgId);
        Map<String, PerformanceRule> merged = new LinkedHashMap<>();
        ruleMapper.selectList(new LambdaQueryWrapper<PerformanceRule>()
                        .eq(PerformanceRule::getSubOrgId, subOrgId)
                        .isNull(PerformanceRule::getSemesterId))
                .forEach(rule -> merged.put(rule.getDimensionKey(), rule));
        if (semesterId != null) {
            ruleMapper.selectList(new LambdaQueryWrapper<PerformanceRule>()
                            .eq(PerformanceRule::getSubOrgId, subOrgId)
                            .eq(PerformanceRule::getSemesterId, semesterId))
                    .forEach(rule -> merged.put(rule.getDimensionKey(), rule));
        }
        return merged;
    }

    /** 加权分 = score × weight（2 位小数，四舍五入）。 */
    public static BigDecimal weightedScore(BigDecimal score, BigDecimal weight) {
        return score.multiply(weight).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 打分落库（快照）：按 weeklyId 物理删除旧得分行后重插（打分仅 SUBMITTED 允许，重打分先驳回再改不在此路径）。
     * 返回加权总分（Σ score×weight，2 位小数）。
     */
    public BigDecimal persistScores(PerformanceWeekly weekly, Map<String, PerformanceRule> ruleMap,
                                    List<PerformanceScore> lines) {
        scoreMapper.delete(new LambdaQueryWrapper<PerformanceScore>()
                .eq(PerformanceScore::getWeeklyId, weekly.getId()));
        BigDecimal total = BigDecimal.ZERO;
        for (PerformanceScore line : lines) {
            PerformanceRule rule = ruleMap.get(line.getDimensionKey());
            line.setWeeklyId(weekly.getId())
                    .setSubOrgId(weekly.getSubOrgId())
                    .setDimensionName(rule.getDimensionName())
                    .setWeight(rule.getWeight())
                    .setMaxScore(rule.getMaxScore())
                    .setWeightedScore(weightedScore(line.getScore(), rule.getWeight()));
            scoreMapper.insert(line);
            total = total.add(line.getWeightedScore());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 学期汇总重算 + 子组织内排名重排 + 周报标签生成（PF07 事务内同步执行）。
     * 排名口径：本子组织本学期全部 summary 按 totalScore 降序（并列同名次）。
     */
    public void recalcSummaryAndRank(PerformanceWeekly weekly) {
        //1.该成员该学期的全部周报（SCORED 取分数与周次，SUBMITTED/REJECTED 计提交数）
        List<PerformanceWeekly> weeklies = weeklyMapper.selectList(new LambdaQueryWrapper<PerformanceWeekly>()
                .eq(PerformanceWeekly::getMemberId, weekly.getMemberId())
                .eq(PerformanceWeekly::getSemesterId, weekly.getSemesterId())
                .eq(PerformanceWeekly::getSubOrgId, weekly.getSubOrgId()));
        List<PerformanceWeekly> scored = weeklies.stream()
                .filter(w -> "SCORED".equals(w.getStatus()) && w.getTotalScore() != null)
                .sorted((a, b) -> Integer.compare(a.getWeekNo(), b.getWeekNo()))
                .toList();
        //2.逐周满分（高效周判定：单周 Σ max_score×weight 快照，从 performance_score 取）
        Map<Long, BigDecimal> weeklyMax = new LinkedHashMap<>();
        scored.forEach(w -> {
            BigDecimal max = scoreMapper.selectList(new LambdaQueryWrapper<PerformanceScore>()
                            .eq(PerformanceScore::getWeeklyId, w.getId())).stream()
                    .map(line -> line.getMaxScore().multiply(line.getWeight()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            weeklyMax.put(w.getId(), max);
        });
        List<String> tags = buildTags(scored, weeklyMax);
        //3.汇总落库（upsert：唯一键 member+semester+subOrg）
        PerformanceSummary summary = summaryMapper.selectOne(new LambdaQueryWrapper<PerformanceSummary>()
                .eq(PerformanceSummary::getMemberId, weekly.getMemberId())
                .eq(PerformanceSummary::getSemesterId, weekly.getSemesterId())
                .eq(PerformanceSummary::getSubOrgId, weekly.getSubOrgId()));
        if (summary == null) {
            summary = new PerformanceSummary()
                    .setMemberId(weekly.getMemberId())
                    .setMemberName(weekly.getMemberName())
                    .setSemesterId(weekly.getSemesterId())
                    .setSubOrgId(weekly.getSubOrgId())
                    .setOrgId(weekly.getSubOrgId());
        }
        BigDecimal totalScore = scored.stream().map(PerformanceWeekly::getTotalScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setSubmittedCount(weeklies.size())
                .setWeekCount(scored.size())
                .setTotalScore(totalScore)
                .setAvgScore(scored.isEmpty() ? BigDecimal.ZERO
                        : totalScore.divide(BigDecimal.valueOf(scored.size()), 2, RoundingMode.HALF_UP))
                .setRecentTrend(trendSlope(scored));
        PerformanceWeekly best = scored.stream()
                .max((a, b) -> a.getTotalScore().compareTo(b.getTotalScore()))
                .orElse(null);
        if (best != null) {
            summary.setBestWeekScore(best.getTotalScore());
            summary.setBestWeekNo(best.getWeekNo());
        } else {
            summary.setBestWeekScore(null);
            summary.setBestWeekNo(null);
        }
        summary.setActivityTags(toTagsJson(tags));
        if (summary.getId() == null) {
            summaryMapper.insert(summary);
        } else {
            summaryMapper.updateById(summary);
        }
        //4.周报标签（活跃度/高效周挂当周，功劳度/连续贡献挂汇总口径同写一份）
        weekly.setActivityTags(toTagsJson(tags));
        weeklyMapper.updateById(weekly);
        //5.子组织内排名重排（所有成员）
        rerankInOrg(weekly.getSemesterId(), weekly.getSubOrgId());
    }

    /** 标签生成（阈值口径见类注释）。 */
    private List<String> buildTags(List<PerformanceWeekly> scored, Map<Long, BigDecimal> weeklyMax) {
        List<String> tags = new ArrayList<>();
        BigDecimal slope = trendSlope(scored);
        if (scored.size() >= 2 && slope.signum() > 0) {
            tags.add("活跃度上升");
        }
        if (scored.size() >= 2 && slope.signum() < 0) {
            tags.add("活跃度下降");
        }
        if (isConsecutive(scored, CONSECUTIVE_WEEKS)) {
            tags.add("连续贡献");
        }
        scored.forEach(w -> {
            BigDecimal max = weeklyMax.get(w.getId());
            if (max != null && max.signum() > 0
                    && w.getTotalScore().compareTo(max.multiply(EFFICIENT_RATIO)) >= 0) {
                tags.add("高效周(第" + w.getWeekNo() + "周)");
            }
        });
        return tags;
    }

    /** 近 4 周趋势斜率：最近 4 个已打分周的最小二乘斜率，不足 2 周返回 0。 */
    private BigDecimal trendSlope(List<PerformanceWeekly> scoredByWeekAsc) {
        List<PerformanceWeekly> recent = scoredByWeekAsc.stream()
                .skip(Math.max(0, scoredByWeekAsc.size() - 4))
                .toList();
        if (recent.size() < 2) {
            return BigDecimal.ZERO;
        }
        int n = recent.size();
        BigDecimal sumX = BigDecimal.ZERO, sumY = BigDecimal.ZERO, sumXY = BigDecimal.ZERO, sumXX = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal x = BigDecimal.valueOf(recent.get(i).getWeekNo());
            BigDecimal y = recent.get(i).getTotalScore();
            sumX = sumX.add(x);
            sumY = sumY.add(y);
            sumXY = sumXY.add(x.multiply(y));
            sumXX = sumXX.add(x.multiply(x));
        }
        BigDecimal denominator = sumXX.multiply(BigDecimal.valueOf(n)).subtract(sumX.multiply(sumX));
        if (denominator.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return sumXY.multiply(BigDecimal.valueOf(n)).subtract(sumX.multiply(sumY))
                .divide(denominator, 3, RoundingMode.HALF_UP);
    }

    /** 连续贡献：按周次连续且均已打分 ≥ n 周。 */
    private boolean isConsecutive(List<PerformanceWeekly> scoredByWeekAsc, int n) {
        if (scoredByWeekAsc.size() < n) {
            return false;
        }
        int streak = 1;
        for (int i = 1; i < scoredByWeekAsc.size(); i++) {
            if (scoredByWeekAsc.get(i).getWeekNo() == scoredByWeekAsc.get(i - 1).getWeekNo() + 1) {
                streak++;
            } else {
                streak = 1;
            }
        }
        return streak >= n;
    }

    /** 子组织内排名重排：totalScore 降序、并列同名次；同时给前 20% 挂"功劳度高"。 */
    private void rerankInOrg(Long semesterId, Long subOrgId) {
        List<PerformanceSummary> all = summaryMapper.selectList(new LambdaQueryWrapper<PerformanceSummary>()
                .eq(PerformanceSummary::getSemesterId, semesterId)
                .eq(PerformanceSummary::getSubOrgId, subOrgId));
        all.sort((a, b) -> b.getTotalScore().compareTo(a.getTotalScore()));
        int topCount = Math.max(1, (int) Math.ceil(all.size() * TOP_RATIO.doubleValue()));
        for (int i = 0; i < all.size(); i++) {
            PerformanceSummary row = all.get(i);
            //并列同名次：与上一名同分取相同名次
            int rank = i > 0 && all.get(i - 1).getTotalScore().compareTo(row.getTotalScore()) == 0
                    ? all.get(i - 1).getRankInOrg() : i + 1;
            List<String> tags = parseTags(row.getActivityTags());
            tags.remove("功劳度高");
            if (i < topCount && row.getTotalScore().signum() > 0) {
                tags.add(0, "功劳度高");
            }
            row.setRankInOrg(rank).setActivityTags(toTagsJson(tags));
            summaryMapper.updateById(row);
        }
    }

    private static String toTagsJson(List<String> tags) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tags);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** activity_tags JSON 列解析（解析失败返回空列表不抛错，展示字段不阻塞业务）。 */
    public static List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return new ArrayList<>(mapper.readValue(json, mapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class)));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
