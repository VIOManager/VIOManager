package com.viomanager.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.approval.dto.ApprovalQueryDTO;
import com.viomanager.approval.dto.ApprovalReadDTO;
import com.viomanager.approval.entity.Approval;
import com.viomanager.approval.entity.ApprovalRecord;
import com.viomanager.approval.mapper.ApprovalMapper;
import com.viomanager.approval.mapper.ApprovalRecordMapper;
import com.viomanager.approval.service.IApprovalQueryService;
import com.viomanager.approval.support.ApprovalUnreadCache;
import com.viomanager.approval.support.UnreadCounter;
import com.viomanager.approval.vo.ApprovalDetailVO;
import com.viomanager.approval.vo.ApprovalRecordVO;
import com.viomanager.approval.vo.ApprovalVO;
import com.viomanager.approval.vo.UnreadCountVO;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.ApprovalErrorCode;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.datascop.OrgScopeSupport;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 审批查询实现。
 * ⚠️ ID 口径：applicant_id / target_member_id 是人员档案 ID，当前用户的对应值
 * 要回 sys_user 查 member_id（token 里没带，绑定关系变更也能实时生效）。
 */
@Slf4j
@Service
public class ApprovalQueryServiceImpl implements IApprovalQueryService {

    @Resource
    private ApprovalMapper approvalMapper;

    @Resource
    private ApprovalRecordMapper approvalRecordMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private OrgScopeSupport orgScopeSupport;

    @Resource
    private UnreadCounter unreadCounter;

    @Resource
    private ApprovalUnreadCache approvalUnreadCache;

    @Override
    public Result<PageResult<ApprovalVO>> list(ApprovalQueryDTO dto) {
        CurrentUser user = UserHolder.getUser();
        //1.按视角拼过滤：TODO 是管理员待办（学生调用直接 403）；MINE / TARGET 按人员 ID
        QueryWrapper<Approval> wrapper = new QueryWrapper<>();
        if ("TODO".equals(dto.getView())) {
            if (!"SUPER_ADMIN".equals(user.getRole()) && !"DEPT_ADMIN".equals(user.getRole())) {
                throw new BizException(CommonErrorCode.FORBIDDEN, "待办视角仅管理员可用");
            }
            if (!"SUPER_ADMIN".equals(user.getRole())) {
                List<Long> subtreeOrgIds = querySubtreeOrgIds(user.getOrgId());
                wrapper.in("approver_org_id", subtreeOrgIds);
            }
        } else if ("MINE".equals(dto.getView())) {
            wrapper.eq("applicant_id", requireMemberId(user));
        } else if ("TARGET".equals(dto.getView())) {
            wrapper.eq("target_member_id", requireMemberId(user));
        } else {
            throw new BizException(CommonErrorCode.PARAM_INVALID,
                    "view 取值不合法：" + dto.getView());
        }
        //2.可选筛选与排序
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            wrapper.eq("status", dto.getStatus());
        }
        if (dto.getBizType() != null && !dto.getBizType().isEmpty()) {
            wrapper.eq("biz_type", dto.getBizType());
        }
        //标题关键词模糊匹配（2026-10-12 lzh 拍板补）
        if (dto.getKeyword() != null && !dto.getKeyword().isEmpty()) {
            wrapper.like("biz_title", dto.getKeyword());
        }
        wrapper.orderByDesc("created_at");
        //3.分页查询转 VO：列表只要 VO 的窄列——biz_payload 快照列宽（详情页才用），
        //分页白拉它最亏（2026-10-20 组长规矩：SQL 禁 SELECT *，隐式全列同理）
        wrapper.select(Approval.class, info -> !"biz_payload".equals(info.getColumn()));
        Page<Approval> page = approvalMapper.selectPage(
                new Page<>(dto.getPage(), dto.getSize()), wrapper);
        List<ApprovalVO> voList = new ArrayList<>();
        for (Approval approval : page.getRecords()) {
            voList.add(toVO(approval, new ApprovalVO()));
        }
        return Result.ok(PageResult.of(voList, page.getTotal(), dto.getPage(), dto.getSize()));
    }

    @Override
    public Result<ApprovalDetailVO> detail(Long id) {
        //1.查单
        Approval approval = approvalMapper.selectById(id);
        if (approval == null) {
            throw new BizException(ApprovalErrorCode.NOT_FOUND_OR_HANDLED);
        }
        //2.看详情的资格：超管 / 发起人 / 目标人 / 审批组织子树内的管理员
        checkViewPermission(approval);
        //3.组装详情：列表字段 + 快照 + 流水（时间正序）
        ApprovalDetailVO vo = (ApprovalDetailVO) toVO(approval, new ApprovalDetailVO());
        vo.setBizPayload(approval.getBizPayload());
        List<ApprovalRecord> records = approvalRecordMapper.selectList(
                new QueryWrapper<ApprovalRecord>().eq("approval_id", id).orderByAsc("created_at"));
        List<ApprovalRecordVO> recordVoList = new ArrayList<>();
        for (ApprovalRecord record : records) {
            ApprovalRecordVO recordVO = new ApprovalRecordVO();
            recordVO.setAction(record.getAction());
            recordVO.setOperatorName(record.getOperatorName());
            recordVO.setOperatorRole(record.getOperatorRole());
            recordVO.setCommentText(record.getCommentText());
            recordVO.setCreatedAt(record.getCreatedAt());
            recordVoList.add(recordVO);
        }
        vo.setRecords(recordVoList);
        return Result.ok(vo);
    }

    @Override
    public Result<Void> markRead(ApprovalReadDTO dto) {
        CurrentUser user = UserHolder.getUser();
        //1.按 asRole 决定置哪个已读位、限定谁的单子
        UpdateWrapper<Approval> update = new UpdateWrapper<>();
        if ("APPLICANT".equals(dto.getAsRole())) {
            update.eq("applicant_id", requireMemberId(user)).set("applicant_read", 1);
        } else if ("TARGET".equals(dto.getAsRole())) {
            update.eq("target_member_id", requireMemberId(user)).set("target_read", 1);
        } else if ("APPROVER".equals(dto.getAsRole())) {
            if (!"SUPER_ADMIN".equals(user.getRole()) && !"DEPT_ADMIN".equals(user.getRole())) {
                throw new BizException(ApprovalErrorCode.NO_PERMISSION);
            }
            if (!"SUPER_ADMIN".equals(user.getRole())) {
                update.in("approver_org_id", querySubtreeOrgIds(user.getOrgId()));
            }
            update.set("approver_read", 1);
        } else {
            throw new BizException(CommonErrorCode.PARAM_INVALID,
                    "asRole 取值不合法：" + dto.getAsRole());
        }
        //2.只动自己名下的这些单子
        update.in("id", dto.getIds());
        approvalMapper.update(null, update);
        return Result.ok();
    }

    @Override
    public Result<UnreadCountVO> unreadCount() {
        CurrentUser user = UserHolder.getUser();
        //1.当前用户没绑人员档案（纯管理账号）→ 两类红点都是 0
        Long memberId = currentMemberId(user);
        if (memberId == null) {
            UnreadCountVO vo = new UnreadCountVO();
            vo.setTodoCount(0);
            vo.setResultCount(0);
            return Result.ok(vo);
        }
        //2.计数口径收口到 UnreadCounter：读路径走 cache-aside（插入点②），miss 现算回填——
        //  与消费者重算回写走同一段计数逻辑，数字不会漂移；开关关时就是现状直查库
        return Result.ok(approvalUnreadCache.get(memberId,
                () -> unreadCounter.countFor(memberId)));
    }

    /** 详情可见性：超管 / 发起人 / 目标人 / 审批组织子树内管理员，其余 6002 */
    private void checkViewPermission(Approval approval) {
        CurrentUser user = UserHolder.getUser();
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        Long memberId = currentMemberId(user);
        if (memberId != null && (memberId.equals(approval.getApplicantId())
                || memberId.equals(approval.getTargetMemberId()))) {
            return;
        }
        if ("DEPT_ADMIN".equals(user.getRole()) && isOrgInSubtree(user.getOrgId(), approval.getApproverOrgId())) {
            return;
        }
        throw new BizException(ApprovalErrorCode.NO_PERMISSION);
    }

    /** 当前用户的人员档案 ID；没绑定返回 null（require 版本直接 403） */
    private Long requireMemberId(CurrentUser user) {
        Long memberId = currentMemberId(user);
        if (memberId == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "当前账号未绑定人员档案");
        }
        return memberId;
    }

    private Long currentMemberId(CurrentUser user) {
        if (user == null || user.getUserId() == null) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT member_id FROM sys_user WHERE id = ? AND deleted = 0",
                Long.class, user.getUserId());
        if (ids.isEmpty() || ids.get(0) == null) {
            return null;
        }
        return ids.get(0);
    }

    /** 管理员组织子树内的组织 ID 列表——判定收口到 OrgScopeSupport（插入点①，Redis cache-aside + 降级直查库） */
    private List<Long> querySubtreeOrgIds(Long orgId) {
        return orgScopeSupport.subtreeIds(orgId);
    }

    /** 判断组织是否在管理员子树内——判定收口到 OrgScopeSupport（插入点①） */
    private boolean isOrgInSubtree(Long adminOrgId, Long orgId) {
        return orgScopeSupport.inSubtree(adminOrgId, orgId);
    }

    /** 实体转 VO（详情 VO 复用同一套列表字段） */
    private ApprovalVO toVO(Approval approval, ApprovalVO vo) {
        vo.setId(approval.getId());
        vo.setBizType(approval.getBizType());
        vo.setBizTitle(approval.getBizTitle());
        vo.setStatus(approval.getStatus());
        vo.setApplicantName(approval.getApplicantName());
        vo.setTargetMemberName(approval.getTargetMemberName());
        vo.setApplyReason(approval.getApplyReason());
        vo.setHandleComment(approval.getHandleComment());
        vo.setHandledAt(approval.getHandledAt());
        vo.setCreatedAt(approval.getCreatedAt());
        return vo;
    }
}
