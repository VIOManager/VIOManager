package com.viomanager.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.viomanager.performance.dto.PerfRuleSaveDTO;
import com.viomanager.performance.entity.PerformanceRule;
import com.viomanager.performance.mapper.PerformanceRuleMapper;
import com.viomanager.performance.service.PerformanceRuleService;
import com.viomanager.performance.support.PerfCalcSupport;
import com.viomanager.performance.vo.PerfRuleVO;
import com.viomanager.schedule.support.SubOrgSupport;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 绩效规则实现。upsert 口径：按 (subOrgId, semesterId 可空, dimensionKey) 先查后写；
 * MySQL 唯一索引对 NULL 不去重，默认规则同键重复由先查兜底（与排班规则同款）。
 */
@Service
public class PerformanceRuleServiceImpl implements PerformanceRuleService {

    @Resource
    private PerformanceRuleMapper ruleMapper;

    @Resource
    private SubOrgSupport subOrgSupport;

    @Resource
    private PerfCalcSupport calcSupport;

    @Override
    public List<PerfRuleVO> list(Long subOrgId, Long semesterId) {
        List<PerformanceRule> rules = new ArrayList<>(calcSupport.effectiveRuleMap(subOrgId, semesterId).values());
        rules.sort((a, b) -> Integer.compare(
                a.getSortNo() == null ? 0 : a.getSortNo(),
                b.getSortNo() == null ? 0 : b.getSortNo()));
        return rules.stream().map(rule -> {
            PerfRuleVO vo = new PerfRuleVO();
            vo.setDimensionKey(rule.getDimensionKey());
            vo.setDimensionName(rule.getDimensionName());
            vo.setWeight(rule.getWeight());
            vo.setMaxScore(rule.getMaxScore());
            vo.setEnabled(rule.getEnabled() != null && rule.getEnabled() == 1);
            vo.setSortNo(rule.getSortNo());
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(PerfRuleSaveDTO dto) {
        subOrgSupport.requireSubOrg(dto.getSubOrgId());
        List<PerformanceRule> existing = ruleMapper.selectList(new LambdaQueryWrapper<PerformanceRule>()
                .eq(PerformanceRule::getSubOrgId, dto.getSubOrgId())
                .eq(dto.getSemesterId() != null, PerformanceRule::getSemesterId, dto.getSemesterId())
                .isNull(dto.getSemesterId() == null, PerformanceRule::getSemesterId));
        java.util.Map<String, PerformanceRule> existingByKey = new java.util.LinkedHashMap<>();
        existing.forEach(rule -> existingByKey.put(rule.getDimensionKey(), rule));
        dto.getItems().forEach(item -> {
            PerformanceRule rule = existingByKey.get(item.getDimensionKey());
            if (rule == null) {
                rule = new PerformanceRule()
                        .setSubOrgId(dto.getSubOrgId())
                        .setSemesterId(dto.getSemesterId())
                        .setDimensionKey(item.getDimensionKey());
                applyItem(rule, item);
                ruleMapper.insert(rule);
            } else {
                applyItem(rule, item);
                ruleMapper.updateById(rule);
            }
        });
    }

    private void applyItem(PerformanceRule rule, PerfRuleSaveDTO.Item item) {
        rule.setDimensionName(item.getDimensionName())
                .setWeight(item.getWeight())
                .setMaxScore(item.getMaxScore())
                .setEnabled(Boolean.TRUE.equals(item.getEnabled()) ? 1 : 0)
                .setSortNo(item.getSortNo() == null ? 0 : item.getSortNo())
                .setDescription(item.getDescription());
    }
}
