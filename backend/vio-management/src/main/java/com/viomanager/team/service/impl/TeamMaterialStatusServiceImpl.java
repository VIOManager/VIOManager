package com.viomanager.team.service.impl;

import com.viomanager.team.service.TeamMaterialStatusService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 跨线回写实现：只 UPDATE team 表的 material_status / material_complete_rate 两列。
 * 未命中（赛队已删/不存在）记 warn 不抛错——C 线材料提交主流程不应因队伍恰好被解散而整体失败。
 */
@Slf4j
@Service
public class TeamMaterialStatusServiceImpl implements TeamMaterialStatusService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void updateMaterialStatus(Long teamId, String materialStatus, BigDecimal completeRate) {
        //1.显式列名只动材料两列；deleted=0 防止复活已删队伍
        int rows = jdbcTemplate.update(
                "UPDATE team SET material_status = ?, material_complete_rate = ? WHERE id = ? AND deleted = 0",
                materialStatus, completeRate, teamId);
        if (rows == 0) {
            log.warn("材料状态回写未命中：teamId={} materialStatus={} completeRate={}",
                    teamId, materialStatus, completeRate);
        }
    }
}
