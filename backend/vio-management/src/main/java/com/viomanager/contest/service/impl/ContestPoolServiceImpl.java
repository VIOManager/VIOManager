package com.viomanager.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.contest.dto.ContestCreateDTO;
import com.viomanager.contest.entity.Contest;
import com.viomanager.contest.entity.ContestSource;
import com.viomanager.contest.mapper.ContestMapper;
import com.viomanager.contest.mapper.ContestSourceMapper;
import com.viomanager.contest.service.IContestCommandService;
import com.viomanager.contest.service.IContestPoolService;
import com.viomanager.contest.support.ContestRuleSupport;
import com.viomanager.contest.vo.ContestDetailVO;
import com.viomanager.contest.vo.PoolItemVO;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 外部比赛待处理池服务实现（P01–P03）。
 * 仅管理端可见（Controller @RequireRole 挡学生）；PENDING 可确认（预填创建 contest + 回填 confirmed_contest_id）或忽略。
 * P01 走 JdbcTemplate 显式列名（禁 SELECT *），parsed_rules 解析委托 {@link ContestRuleSupport#parseJson}。
 */
@Slf4j
@Service
public class ContestPoolServiceImpl implements IContestPoolService {

    private static final String POOL_SELECT =
            "SELECT id, title, source_type, source_url, raw_content, contest_time, parsed_rules, "
                    + "status, confirmed_contest_id, fetched_at, handled_at "
                    + "FROM contest_source WHERE deleted = 0";

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ContestSourceMapper contestSourceMapper;
    @Resource
    private ContestMapper contestMapper;
    @Resource
    private IContestCommandService commandService;
    @Resource
    private ContestRuleSupport ruleSupport;

    // ────────────────────────── P01 列表 ──────────────────────────

    @Override
    public Result<List<PoolItemVO>> listPool(String status) {
        StringBuilder sql = new StringBuilder(POOL_SELECT);
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY fetched_at DESC");
        List<PoolItemVO> voList = jdbcTemplate.query(sql.toString(), (rs, n) -> {
            PoolItemVO vo = new PoolItemVO();
            vo.setId(rs.getLong("id"));
            vo.setTitle(rs.getString("title"));
            vo.setSource(rs.getString("source_type"));
            vo.setFetchedAt(rs.getObject("fetched_at", LocalDateTime.class));
            vo.setContent(rs.getString("raw_content"));
            vo.setClues(ruleSupport.parseJson(rs.getString("parsed_rules")));
            vo.setStatus(rs.getString("status"));
            vo.setConfirmedContestId(rs.getObject("confirmed_contest_id", Long.class));
            return vo;
        }, params.toArray());
        return Result.ok(voList);
    }

    // ────────────────────────── P02 确认 ──────────────────────────

    @Override
    @Transactional
    public Result<ContestDetailVO> confirmPool(Long id, ContestCreateDTO dto) {
        ContestSource source = loadSourceEntity(id);
        if (!"PENDING".equals(source.getStatus())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "仅 PENDING 状态可确认");
        }
        // 复用 C03 创建逻辑（含学期/模板校验、contact 拆分、规则 JSON、status=DRAFT），再覆盖来源标记
        ContestDetailVO vo = commandService.createContest(dto);
        UpdateWrapper<Contest> cu = new UpdateWrapper<>();
        cu.eq("id", vo.getId()).eq("deleted", 0)
                .set("source_type", "CRAWLER_POOL")
                .set("source_ref", String.valueOf(id));
        contestMapper.update(null, cu);
        UpdateWrapper<ContestSource> su = new UpdateWrapper<>();
        su.eq("id", id).eq("deleted", 0).eq("status", "PENDING")
                .set("confirmed_contest_id", vo.getId())
                .set("status", "CONFIRMED")
                .set("handled_at", LocalDateTime.now())
                .set("handled_by", UserHolder.getUser().getUserId());
        contestSourceMapper.update(null, su);
        return Result.ok(vo);
    }

    // ────────────────────────── P03 忽略 ──────────────────────────

    @Override
    @Transactional
    public Result<Void> ignorePool(Long id) {
        ContestSource source = loadSourceEntity(id);
        if (!"PENDING".equals(source.getStatus())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "仅 PENDING 状态可忽略");
        }
        UpdateWrapper<ContestSource> uw = new UpdateWrapper<>();
        uw.eq("id", id).eq("deleted", 0).eq("status", "PENDING")
                .set("status", "IGNORED")
                .set("handled_at", LocalDateTime.now())
                .set("handled_by", UserHolder.getUser().getUserId());
        contestSourceMapper.update(null, uw);
        return Result.ok();
    }

    // ────────────────────────── 内部辅助 ──────────────────────────

    private ContestSource loadSourceEntity(Long id) {
        ContestSource source = contestSourceMapper.selectById(id);
        if (source == null || (source.getDeleted() != null && source.getDeleted() == 1)) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "待处理比赛不存在");
        }
        return source;
    }
}
