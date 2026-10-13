package com.viomanager.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.util.ParamsJsonUtil;
import com.viomanager.contest.dto.ContestCreateDTO;
import com.viomanager.contest.dto.TrackCreateDTO;
import com.viomanager.contest.entity.Contest;
import com.viomanager.contest.entity.ContestTrack;
import com.viomanager.contest.mapper.ContestMapper;
import com.viomanager.contest.mapper.ContestTrackMapper;
import com.viomanager.contest.service.IContestCommandService;
import com.viomanager.contest.service.IContestQueryService;
import com.viomanager.contest.support.ContestCommandSupport;
import com.viomanager.contest.support.ContestRuleSupport;
import com.viomanager.contest.support.ContestRuleSupport.RuleBundle;
import com.viomanager.contest.vo.ContestDetailVO;
import com.viomanager.contest.vo.EffectiveRuleVO;
import com.viomanager.contest.vo.TrackVO;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 比赛写服务实现（C03–C09）。
 * 状态机：DRAFT → PUBLISHED → REGISTERING → RUNNING → FINISHED → ARCHIVED。
 * 写操作即时生效（不产生审批单）；DRAFT→PUBLISHED 发布前逐项检查缺项（4001）。
 * 插入走 BaseMapper.insert，更新走 UpdateWrapper，规则 JSON 转换用 ParamsJsonUtil。
 */
@Slf4j
@Service
public class ContestCommandServiceImpl implements IContestCommandService {

    @Resource
    private ContestMapper contestMapper;
    @Resource
    private ContestTrackMapper contestTrackMapper;
    @Resource
    private ContestCommandSupport cmdSupport;
    @Resource
    private ContestRuleSupport ruleSupport;
    @Resource
    private IContestQueryService queryService;

    // ────────────────────────── C03 创建 ──────────────────────────

    @Override
    @Transactional
    public ContestDetailVO createContest(ContestCreateDTO dto) {
        cmdSupport.ensureSemesterExists(dto.getSemesterId());
        cmdSupport.ensureTemplateExists(dto.getTemplateId());
        Contest contest = new Contest();
        contest.setName(dto.getName());
        contest.setTemplateId(dto.getTemplateId());
        contest.setSemesterId(dto.getSemesterId());
        contest.setOrgId(UserHolder.getUser().getOrgId());
        contest.setStatus("DRAFT");
        contest.setRegisterStartAt(dto.getSignupStart());
        contest.setRegisterEndAt(dto.getSignupEnd());
        contest.setContestStartAt(dto.getContestStart());
        contest.setContestEndAt(dto.getContestEnd());
        String[] contact = parseContact(dto.getContact());
        contest.setContactName(contact[0]);
        contest.setContactPhone(contact[1]);
        contest.setDescription(dto.getDescription());
        contest.setOverrideRules(toJson(dto.getOverrideRules()));
        contest.setSourceType("MANUAL");
        contestMapper.insert(contest);
        return queryService.getContestDetail(contest.getId()).getData();
    }

    // ────────────────────────── C04 编辑 ──────────────────────────

    @Override
    @Transactional
    public ContestDetailVO updateContest(Long id, ContestCreateDTO dto) {
        Contest contest = loadContestEntity(id);
        if ("ARCHIVED".equals(contest.getStatus())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "已归档比赛不可编辑");
        }
        cmdSupport.ensureSemesterExists(dto.getSemesterId());
        cmdSupport.ensureTemplateExists(dto.getTemplateId());
        String[] contact = parseContact(dto.getContact());
        UpdateWrapper<Contest> uw = new UpdateWrapper<>();
        uw.eq("id", id).eq("deleted", 0)
                .set("name", dto.getName())
                .set("semester_id", dto.getSemesterId())
                .set("template_id", dto.getTemplateId())
                .set("register_start_at", dto.getSignupStart())
                .set("register_end_at", dto.getSignupEnd())
                .set("contest_start_at", dto.getContestStart())
                .set("contest_end_at", dto.getContestEnd())
                .set("contact_name", contact[0])
                .set("contact_phone", contact[1])
                .set("description", dto.getDescription())
                .set("override_rules", toJson(dto.getOverrideRules()));
        contestMapper.update(null, uw);
        return queryService.getContestDetail(id).getData();
    }

    // ────────────────────────── C05 删除 ──────────────────────────

    @Override
    @Transactional
    public void deleteContest(Long id) {
        Contest contest = loadContestEntity(id);
        if (!"DRAFT".equals(contest.getStatus())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "仅 DRAFT 状态可删");
        }
        contestMapper.deleteById(id);
    }

    // ────────────────────────── C06 状态流转 ──────────────────────────

    @Override
    @Transactional
    public void transitionStatus(Long id, String to) {
        Contest contest = loadContestEntity(id);
        cmdSupport.ensureStatusTransition(contest.getStatus(), to);
        if ("DRAFT".equals(contest.getStatus()) && "PUBLISHED".equals(to)) {
            ensurePublishReady(contest);
        }
        UpdateWrapper<Contest> uw = new UpdateWrapper<>();
        uw.eq("id", id).eq("deleted", 0).set("status", to);
        if ("ARCHIVED".equals(to)) {
            uw.set("archived_at", LocalDateTime.now());
        }
        contestMapper.update(null, uw);
    }

    // ────────────────────────── C07 添加赛道 ──────────────────────────

    @Override
    @Transactional
    public TrackVO createTrack(Long contestId, TrackCreateDTO dto) {
        loadContestEntity(contestId);
        cmdSupport.ensureTrackNameUnique(contestId, dto.getName(), null);
        ContestTrack track = new ContestTrack();
        track.setContestId(contestId);
        track.setName(dto.getName());
        track.setCode(dto.getCode());
        track.setDescription(dto.getDescription());
        track.setOverrideRules(toJson(dto.getOverrideRules()));
        track.setSortNo(0);
        track.setStatus(1);
        contestTrackMapper.insert(track);
        return loadTrackVO(track.getId());
    }

    // ────────────────────────── C08 编辑赛道 ──────────────────────────

    @Override
    @Transactional
    public TrackVO updateTrack(Long contestId, Long trackId, TrackCreateDTO dto) {
        cmdSupport.ensureTrackBelongsToContest(trackId, contestId);
        cmdSupport.ensureTrackNameUnique(contestId, dto.getName(), trackId);
        UpdateWrapper<ContestTrack> uw = new UpdateWrapper<>();
        uw.eq("id", trackId).eq("contest_id", contestId).eq("deleted", 0)
                .set("name", dto.getName())
                .set("code", dto.getCode())
                .set("description", dto.getDescription())
                .set("override_rules", toJson(dto.getOverrideRules()));
        contestTrackMapper.update(null, uw);
        return loadTrackVO(trackId);
    }

    // ────────────────────────── C09 删除赛道 ──────────────────────────

    @Override
    @Transactional
    public void deleteTrack(Long contestId, Long trackId) {
        cmdSupport.ensureTrackBelongsToContest(trackId, contestId);
        if (cmdSupport.countTeamsOnTrack(trackId) > 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "赛道下有赛队，不可删除");
        }
        contestTrackMapper.deleteById(trackId);
    }

    // ────────────────────────── 内部辅助 ──────────────────────────

    /** DRAFT→PUBLISHED 发布前检查：逐项指出缺项，4001 */
    private void ensurePublishReady(Contest contest) {
        List<String> missing = new ArrayList<>();
        if (contest.getName() == null || contest.getName().isBlank()) {
            missing.add("比赛名称");
        }
        if (contest.getRegisterStartAt() == null || contest.getRegisterEndAt() == null) {
            missing.add("报名时间");
        }
        if (contest.getContestStartAt() == null || contest.getContestEndAt() == null) {
            missing.add("比赛时间");
        }
        boolean hasRules = contest.getTemplateId() != null
                || (contest.getOverrideRules() != null && !contest.getOverrideRules().isBlank());
        if (!hasRules) {
            missing.add("规则配置");
        }
        if (!missing.isEmpty()) {
            throw new BizException(TeamErrorCode.CONTEST_STATE_NOT_ALLOWED,
                    "发布前检查未通过：" + String.join("、", missing));
        }
    }

    private Contest loadContestEntity(Long id) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null || (contest.getDeleted() != null && contest.getDeleted() == 1)) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "比赛不存在");
        }
        return contest;
    }

    private TrackVO loadTrackVO(Long trackId) {
        ContestTrack track = contestTrackMapper.selectById(trackId);
        if (track == null || (track.getDeleted() != null && track.getDeleted() == 1)) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "赛道不存在");
        }
        RuleBundle contestBundle = ruleSupport.loadContestRules(track.getContestId());
        RuleBundle trackBundle = ruleSupport.loadTrackRules(contestBundle, track.getOverrideRules());
        TrackVO vo = new TrackVO();
        vo.setId(track.getId());
        vo.setName(track.getName());
        vo.setCode(track.getCode());
        vo.setDescription(track.getDescription());
        vo.setOverrideRules(ruleSupport.parseJson(track.getOverrideRules()));
        vo.setRules(toEffectiveRules(trackBundle));
        vo.setTeamCount(cmdSupport.countTeamsOnTrack(trackId));
        return vo;
    }

    /** 拆分前端 contact 单字段 → [contactName, contactPhone]，按最后一个空格切分 */
    private String[] parseContact(String contact) {
        if (contact == null || contact.isBlank()) {
            return new String[]{null, null};
        }
        int idx = contact.lastIndexOf(' ');
        if (idx < 0) {
            return new String[]{contact.trim(), null};
        }
        return new String[]{contact.substring(0, idx).trim(), contact.substring(idx + 1).trim()};
    }

    private List<EffectiveRuleVO> toEffectiveRules(RuleBundle bundle) {
        List<EffectiveRuleVO> list = new ArrayList<>(bundle.getRules().size());
        bundle.getRules().forEach((k, v) -> {
            EffectiveRuleVO item = new EffectiveRuleVO();
            item.setKey(k);
            item.setValue(v);
            item.setSource(bundle.getSources().get(k));
            list.add(item);
        });
        return list;
    }

    private String toJson(Map<String, Object> rules) {
        return rules == null ? null : ParamsJsonUtil.toJson(rules);
    }
}
