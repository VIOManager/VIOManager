package com.viomanager.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.viomanager.schedule.dto.ScheduleRuleSaveDTO;
import com.viomanager.schedule.entity.ScheduleRule;
import com.viomanager.schedule.mapper.ScheduleRuleMapper;
import com.viomanager.schedule.service.ScheduleRuleService;
import com.viomanager.schedule.support.SubOrgSupport;
import com.viomanager.schedule.vo.ScheduleRuleVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 排班规则实现。upsert 口径：按 (subOrgId, semesterId 可空, weekday, slotIndex) 先查后写；
 * MySQL 唯一索引对 NULL 不去重，默认规则（semester_id NULL）同键重复由这里的先查兜底。
 */
@Service
public class ScheduleRuleServiceImpl implements ScheduleRuleService {

    @Resource
    private ScheduleRuleMapper ruleMapper;

    @Resource
    private SubOrgSupport subOrgSupport;

    @Override
    public List<ScheduleRuleVO> list(Long subOrgId, Long semesterId) {
        Map<String, ScheduleRule> effective = effectiveRuleMap(subOrgId, semesterId);
        List<ScheduleRuleVO> result = new ArrayList<>(35);
        for (int weekday = 1; weekday <= 7; weekday++) {
            for (int slotIndex = 1; slotIndex <= 5; slotIndex++) {
                ScheduleRule rule = effective.get(key(weekday, slotIndex));
                ScheduleRuleVO vo = new ScheduleRuleVO();
                vo.setWeekday(weekday);
                vo.setSlotIndex(slotIndex);
                if (rule == null) {
                    //未配置时段：视为不启用（接口清单 §5），人数上下限无意义置 0
                    vo.setEnabled(false);
                    vo.setMinPerson(0);
                    vo.setMaxPerson(0);
                } else {
                    vo.setEnabled(rule.getEnabled() != null && rule.getEnabled() == 1);
                    vo.setMinPerson(rule.getMinPerson());
                    vo.setMaxPerson(rule.getMaxPerson());
                }
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ScheduleRuleSaveDTO dto) {
        subOrgSupport.requireSubOrg(dto.getSubOrgId());
        //先取本子组织该学期维度下已有规则，键 → 实体，供 upsert 命中
        Map<String, ScheduleRule> existing = new LinkedHashMap<>();
        ruleMapper.selectList(new LambdaQueryWrapper<ScheduleRule>()
                        .eq(ScheduleRule::getSubOrgId, dto.getSubOrgId())
                        .eq(dto.getSemesterId() != null, ScheduleRule::getSemesterId, dto.getSemesterId())
                        .isNull(dto.getSemesterId() == null, ScheduleRule::getSemesterId))
                .forEach(rule -> existing.put(key(rule.getWeekday(), rule.getSlotIndex()), rule));
        dto.getItems().forEach(item -> {
            ScheduleRule rule = existing.get(key(item.getWeekday(), item.getSlotIndex()));
            if (rule == null) {
                rule = new ScheduleRule()
                        .setSubOrgId(dto.getSubOrgId())
                        .setSemesterId(dto.getSemesterId())
                        .setWeekday(item.getWeekday())
                        .setSlotIndex(item.getSlotIndex());
                applyItem(rule, item);
                ruleMapper.insert(rule);
            } else {
                applyItem(rule, item);
                ruleMapper.updateById(rule);
            }
        });
    }

    @Override
    public Map<String, ScheduleRule> effectiveRuleMap(Long subOrgId, Long semesterId) {
        subOrgSupport.requireSubOrg(subOrgId);
        Map<String, ScheduleRule> merged = new LinkedHashMap<>();
        //默认规则先铺底，学期特定规则后覆盖（接口清单 §5 匹配规则）
        ruleMapper.selectList(new LambdaQueryWrapper<ScheduleRule>()
                        .eq(ScheduleRule::getSubOrgId, subOrgId)
                        .isNull(ScheduleRule::getSemesterId))
                .forEach(rule -> merged.put(key(rule.getWeekday(), rule.getSlotIndex()), rule));
        if (semesterId != null) {
            ruleMapper.selectList(new LambdaQueryWrapper<ScheduleRule>()
                            .eq(ScheduleRule::getSubOrgId, subOrgId)
                            .eq(ScheduleRule::getSemesterId, semesterId))
                    .forEach(rule -> merged.put(key(rule.getWeekday(), rule.getSlotIndex()), rule));
        }
        return merged;
    }

    private void applyItem(ScheduleRule rule, ScheduleRuleSaveDTO.Item item) {
        rule.setMinPerson(item.getMinPerson())
                .setMaxPerson(item.getMaxPerson())
                .setEnabled(Boolean.TRUE.equals(item.getEnabled()) ? 1 : 0)
                .setRemark(item.getRemark());
    }

    private String key(int weekday, int slotIndex) {
        return weekday + "-" + slotIndex;
    }
}
