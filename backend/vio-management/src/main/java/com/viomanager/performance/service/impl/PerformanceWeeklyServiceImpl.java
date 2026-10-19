package com.viomanager.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.PerformanceErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.file.entity.FileObject;
import com.viomanager.file.service.IFileService;
import com.viomanager.performance.dto.WeeklyResubmitDTO;
import com.viomanager.performance.dto.WeeklySubmitDTO;
import com.viomanager.performance.entity.PerformanceScore;
import com.viomanager.performance.entity.PerformanceWeekly;
import com.viomanager.performance.mapper.PerformanceScoreMapper;
import com.viomanager.performance.mapper.PerformanceWeeklyMapper;
import com.viomanager.performance.service.PerformanceWeeklyService;
import com.viomanager.performance.vo.WeeklyDetailVO;
import com.viomanager.performance.vo.WeeklyItemVO;
import com.viomanager.schedule.support.ScheduleMemberSupport;
import com.viomanager.schedule.support.SemesterSupport;
import com.viomanager.schedule.support.SubOrgSupport;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 周报服务实现。数据范围：学生仅本人（member_id）；管理端部门管理员本子树（org_id = sub_org_id）。
 * 当前学期一律取 semester 表 ACTIVE 行（PF01/PF06 的 semesterId 缺省口径）。
 */
@Service
public class PerformanceWeeklyServiceImpl implements PerformanceWeeklyService {

    @Resource
    private PerformanceWeeklyMapper weeklyMapper;

    @Resource
    private PerformanceScoreMapper scoreMapper;

    @Resource
    private ScheduleMemberSupport memberSupport;

    @Resource
    private SemesterSupport semesterSupport;

    @Resource
    private SubOrgSupport subOrgSupport;

    @Resource
    private IFileService fileService;

    @Override
    public List<WeeklyItemVO> myWeeklies(Long semesterId, String status) {
        Long memberId = memberSupport.requireCurrentMemberId();
        Long effectiveSemester = semesterId != null ? semesterId : requireCurrentSemesterId();
        List<PerformanceWeekly> rows = weeklyMapper.selectList(new LambdaQueryWrapper<PerformanceWeekly>()
                .eq(PerformanceWeekly::getMemberId, memberId)
                .eq(PerformanceWeekly::getSemesterId, effectiveSemester)
                .eq(status != null && !status.isBlank(), PerformanceWeekly::getStatus, status)
                .orderByAsc(PerformanceWeekly::getWeekNo));
        return toItems(rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(WeeklySubmitDTO dto) {
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberSupport.requireCurrentMemberId();
        Long semesterId = requireCurrentSemesterId();
        semesterSupport.checkWeekInRange(semesterId, dto.getWeekNo(), PerformanceErrorCode.PERF_WEEK_OUT_OF_RANGE);
        ScheduleMemberSupport.MemberBasic member = memberSupport.requireMember(memberId);
        if (member.orgId() == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "当前人员未分配子组织，无法提交周报");
        }
        subOrgSupport.requireSubOrg(member.orgId());
        //同周已有 SUBMITTED/SCORED 拒绝（8041）；REJECTED 记录走 PF03 重提，不另开新记录
        Long existing = weeklyMapper.selectCount(new LambdaQueryWrapper<PerformanceWeekly>()
                .eq(PerformanceWeekly::getMemberId, memberId)
                .eq(PerformanceWeekly::getSemesterId, semesterId)
                .eq(PerformanceWeekly::getWeekNo, dto.getWeekNo())
                .eq(PerformanceWeekly::getSubOrgId, member.orgId())
                .in(PerformanceWeekly::getStatus, "SUBMITTED", "SCORED"));
        if (existing > 0) {
            throw new BizException(PerformanceErrorCode.WEEKLY_DUPLICATE);
        }
        PerformanceWeekly weekly = new PerformanceWeekly()
                .setMemberId(memberId)
                .setMemberName(member.name())
                .setSemesterId(semesterId)
                .setWeekNo(dto.getWeekNo())
                .setSubOrgId(member.orgId())
                .setOrgId(member.orgId())
                .setWorkContent(dto.getWorkContent())
                .setStatus("SUBMITTED")
                .setSubmittedAt(LocalDateTime.now());
        applyAttachment(weekly, dto.getAttachFileId(), user.getUserId());
        weeklyMapper.insert(weekly);
        return weekly.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long id, WeeklyResubmitDTO dto) {
        CurrentUser user = UserHolder.getUser();
        PerformanceWeekly weekly = requireWeekly(id);
        Long memberId = memberSupport.requireCurrentMemberId();
        if (!weekly.getMemberId().equals(memberId)) {
            throw new BizException(PerformanceErrorCode.NOT_WEEKLY_OWNER);
        }
        if (!"REJECTED".equals(weekly.getStatus())) {
            throw new BizException(PerformanceErrorCode.WEEKLY_STATUS_NOT_ALLOWED, "仅被驳回的周报可重新提交");
        }
        weekly.setWorkContent(dto.getWorkContent())
                .setStatus("SUBMITTED")
                .setSubmittedAt(LocalDateTime.now());
        applyAttachment(weekly, dto.getAttachFileId(), user.getUserId());
        weeklyMapper.updateById(weekly);
    }

    @Override
    public WeeklyDetailVO detail(Long id) {
        PerformanceWeekly weekly = requireWeekly(id);
        checkReadable(weekly);
        WeeklyDetailVO vo = new WeeklyDetailVO();
        fillItem(vo, weekly, subOrgNames(List.of(weekly)));
        vo.setWorkContent(weekly.getWorkContent());
        vo.setAttachFileId(weekly.getAttachFileId());
        vo.setAttachFileName(weekly.getAttachFileName());
        vo.setScoreComment(weekly.getScoreComment());
        vo.setRejectReason(weekly.getRejectReason());
        List<WeeklyDetailVO.ScoreLineVO> scores = new ArrayList<>();
        if ("SCORED".equals(weekly.getStatus())) {
            scoreMapper.selectList(new LambdaQueryWrapper<PerformanceScore>()
                            .eq(PerformanceScore::getWeeklyId, weekly.getId()))
                    .forEach(line -> {
                        WeeklyDetailVO.ScoreLineVO lineVO = new WeeklyDetailVO.ScoreLineVO();
                        lineVO.setDimensionKey(line.getDimensionKey());
                        lineVO.setDimensionName(line.getDimensionName());
                        lineVO.setScore(line.getScore());
                        lineVO.setMaxScore(line.getMaxScore());
                        lineVO.setWeight(line.getWeight());
                        lineVO.setCommentText(line.getCommentText());
                        scores.add(lineVO);
                    });
        }
        vo.setScores(scores);
        return vo;
    }

    @Override
    public List<WeeklyItemVO> adminList(Long semesterId, Integer weekNo, Long subOrgId, String status,
                                        String memberKeyword) {
        List<Long> visible = subOrgSupport.visibleOrgIds(UserHolder.getUser());
        Long effectiveSemester = semesterId != null ? semesterId : requireCurrentSemesterId();
        List<PerformanceWeekly> rows = weeklyMapper.selectList(new LambdaQueryWrapper<PerformanceWeekly>()
                .eq(PerformanceWeekly::getSemesterId, effectiveSemester)
                .eq(weekNo != null, PerformanceWeekly::getWeekNo, weekNo)
                .eq(subOrgId != null, PerformanceWeekly::getSubOrgId, subOrgId)
                .eq(status != null && !status.isBlank(), PerformanceWeekly::getStatus, status)
                .like(memberKeyword != null && !memberKeyword.isBlank(),
                        PerformanceWeekly::getMemberName, memberKeyword)
                .in(visible != null, PerformanceWeekly::getOrgId, visible)
                .orderByAsc(PerformanceWeekly::getWeekNo)
                .orderByAsc(PerformanceWeekly::getMemberId));
        return toItems(rows);
    }

    /** 附件归属校验与冗余名回填：fileId 必须为当前登录人上传（骨架 loadOwnedFile 口径）。 */
    private void applyAttachment(PerformanceWeekly weekly, Long attachFileId, Long userId) {
        if (attachFileId == null) {
            return;
        }
        FileObject file = fileService.loadOwnedFile(attachFileId, userId);
        weekly.setAttachFileId(file.getId())
                .setAttachFileName(file.getOriginalName());
    }

    private List<WeeklyItemVO> toItems(List<PerformanceWeekly> rows) {
        Map<Long, String> orgNames = subOrgNames(rows);
        List<WeeklyItemVO> result = new ArrayList<>();
        rows.forEach(row -> {
            WeeklyItemVO vo = new WeeklyItemVO();
            fillItem(vo, row, orgNames);
            result.add(vo);
        });
        return result;
    }

    private void fillItem(WeeklyItemVO vo, PerformanceWeekly row, Map<Long, String> orgNames) {
        vo.setId(row.getId());
        vo.setMemberId(row.getMemberId());
        vo.setMemberName(row.getMemberName());
        vo.setSubOrgId(row.getSubOrgId());
        vo.setSubOrgName(orgNames.getOrDefault(row.getSubOrgId(), "未知组织"));
        vo.setSemesterId(row.getSemesterId());
        vo.setWeekNo(row.getWeekNo());
        vo.setStatus(row.getStatus());
        vo.setTotalScore(row.getTotalScore());
        vo.setSubmittedAt(row.getSubmittedAt());
    }

    private Map<Long, String> subOrgNames(List<PerformanceWeekly> rows) {
        return subOrgSupport.orgNames(rows.stream().map(PerformanceWeekly::getSubOrgId).distinct().toList());
    }

    /** 可读性：本人或管理员（部门管理员限本子树，超管全部）。 */
    private void checkReadable(PerformanceWeekly weekly) {
        CurrentUser user = UserHolder.getUser();
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        if ("STUDENT".equals(user.getRole())) {
            Long memberId = memberSupport.requireCurrentMemberId();
            if (!weekly.getMemberId().equals(memberId)) {
                throw new BizException(CommonErrorCode.FORBIDDEN, "仅本人可查看自己的周报");
            }
            return;
        }
        List<Long> visible = subOrgSupport.visibleOrgIds(user);
        if (visible != null && !visible.contains(weekly.getOrgId())) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权查看该子组织的周报");
        }
    }

    private PerformanceWeekly requireWeekly(Long id) {
        PerformanceWeekly weekly = weeklyMapper.selectById(id);
        if (weekly == null) {
            throw new BizException(PerformanceErrorCode.WEEKLY_NOT_FOUND);
        }
        return weekly;
    }

    private Long requireCurrentSemesterId() {
        SemesterSupport.SemesterRow current = semesterSupport.currentActive();
        if (current == null) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "当前无激活学期");
        }
        return current.id();
    }
}
