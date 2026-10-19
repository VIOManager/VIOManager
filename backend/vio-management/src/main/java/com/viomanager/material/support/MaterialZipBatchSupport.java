package com.viomanager.material.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.enums.MaterialTypeEnum;
import com.viomanager.material.dto.MaterialBatchUploadDTO;
import com.viomanager.material.entity.MaterialRequirement;
import com.viomanager.material.entity.MaterialSubmission;
import com.viomanager.material.entity.MaterialTask;
import com.viomanager.material.mapper.MaterialRequirementMapper;
import com.viomanager.material.mapper.MaterialSubmissionMapper;
import com.viomanager.material.service.MaterialCompletionService;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamRow;
import com.viomanager.material.vo.BatchMatchResultVO;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * zip 批量上传支撑（MS03）：两层匹配 + 未交要求的落库。
 * 匹配规则：名字（zip 条目名 / 兜底的压缩包名）包含要求类型的中文名（MaterialTypeEnum.labelOf）。
 *
 * ⚠️ 两层匹配（C 线接口清单 §3 MS03）：
 *  - 第一层（优先）：zip 条目名——由调用方（MaterialSubmissionServiceImpl.batchUpload）**在事务外**
 *    经骨架文件服务 openCheckedStream 读出后传入（MinIO 网络 IO 不占数据库事务）；
 *  - 第二层（兜底）：读流失败（对象存储不可用 / 染毒等）时调用方拿压缩包名本身当唯一"条目名"
 *    做包含匹配，如"报名表+项目书.zip"能匹配到报名表、项目书两条要求；
 *    一个要求都没匹配上时，unmatched 就是压缩包名本身。
 *
 * ⚠️ 事务边界：公开入口是 persistMatches（@Transactional，落库 + 齐全率重算同事务）；
 * matchAndInsert 已收为私有，防止绕过事务直接调用。
 */
@Slf4j
@Component
public class MaterialZipBatchSupport {

    @Resource
    private MaterialRequirementMapper requirementMapper;

    @Resource
    private MaterialSubmissionMapper submissionMapper;

    @Resource
    private MaterialCompletionService completionService;

    /**
     * 事务边界（MS03）：条目名已由调用方在事务外读好传入，本方法只做两层匹配落库 + 齐全率重算，
     * 网络 IO 不再占用数据库事务（重算失败提交一并回滚）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchMatchResultVO persistMatches(MaterialTask task, TeamRow team, Long memberId, String realName,
                                             MaterialBatchUploadDTO dto, long zipFileSize, List<String> names) {
        BatchMatchResultVO vo = matchAndInsert(task, team, memberId, realName, dto, zipFileSize, names);
        completionService.recompute(dto.getTeamId());
        return vo;
    }

    /**
     * 两层匹配并落库（persistMatches 事务内）。入参校验已由调用方按 MS02 同口径 7 步完成，
     * 条目名 names 也已由调用方在事务外读好（第一层真实解压 / 第二层压缩包名兜底）。
     * 已有 is_current=1 提交的要求跳过不重复覆盖；未交的插入合成提交记录
     * （fileName = 队名-类型中文名.zip、fileSize=0、多条要求共用压缩包 fileId）。
     * 匹配到的要求（含已交过被跳过的）记入 matched；没匹配到任何要求的条目名记入 unmatched。
     */
    private BatchMatchResultVO matchAndInsert(MaterialTask task, TeamRow team, Long memberId,
                                              String realName, MaterialBatchUploadDTO dto,
                                              long zipFileSize, List<String> names) {
        //1.任务下全部未删要求（sort_no / id 升序）+ 该队已有当前版的要求集（已交跳过的判定依据）
        List<MaterialRequirement> reqs = requirementMapper.selectList(
                new QueryWrapper<MaterialRequirement>().eq("task_id", dto.getTaskId())
                        .orderByAsc("sort_no").orderByAsc("id"));
        //并发防线（与 MS02 / A06 同一把锁，见 MaterialSubmissionMapper 注释）：先逐要求锁住该队的
        //提交行再读已交集合——否则两个并发批量都读到"未交"，各插一条 version_no=1 / is_current=1
        for (MaterialRequirement req : reqs) {
            submissionMapper.lockByRequirementAndTeam(req.getId(), dto.getTeamId());
        }
        Set<Long> submittedReqIds = new HashSet<>(
                submissionMapper.selectCurrentRequirementIds(dto.getTeamId()));
        //2.逐要求匹配：命中即记 matched；未交过的落库（versionNo 取历史最大 +1，无历史即为 1）
        List<BatchMatchResultVO.MatchedItem> matched = new ArrayList<>();
        for (MaterialRequirement req : reqs) {
            String label = MaterialTypeEnum.labelOf(req.getMaterialType());
            if (!matchesAnyName(names, label)) {
                continue;
            }
            matched.add(new BatchMatchResultVO.MatchedItem().setRequirementId(req.getId())
                    .setMaterialType(req.getMaterialType()).setRequirementName(req.getName()));
            if (submittedReqIds.contains(req.getId())) {
                continue;
            }
            Integer maxVersion = submissionMapper.selectMaxVersionNo(req.getId(), dto.getTeamId());
            MaterialSubmission submission = new MaterialSubmission();
            submission.setRequirementId(req.getId());
            submission.setTeamId(dto.getTeamId());
            submission.setTaskId(task.getId());
            submission.setContestId(task.getContestId());
            submission.setSemesterId(task.getSemesterId());
            submission.setOrgId(team.getOrgId());
            submission.setFileId(dto.getFileId());
            //兜底层（以及真实解压层）落库的 fileId 都是整个压缩包，取不出单个条目，
            //所以文件名后缀用 .zip、大小用压缩包真实大小 —— 否则会出现"记录名是 .pdf、
            //下载下来却是 zip"的误导，MS04 下载的文件名也与内容不符。
            submission.setFileName(team.getName() + "-" + label + ".zip");
            submission.setFileSize(zipFileSize);
            submission.setIsCurrent(1);
            submission.setVersionNo((maxVersion == null ? 0 : maxVersion) + 1);
            submission.setSubmittedBy(memberId);
            submission.setSubmittedByName(realName);
            submission.setSourceZipName(dto.getFileName());
            //submittedAt 必须手工赋值：AuditMetaObjectHandler 只填四个审计列，不管业务时间列
            submission.setSubmittedAt(LocalDateTime.now());
            submissionMapper.insert(submission);
        }
        //3.没匹配到任何要求的条目名 → unmatched（兜底层里就是压缩包名本身）
        List<String> unmatched = new ArrayList<>();
        for (String name : names) {
            if (!matchesAnyRequirement(name, reqs)) {
                unmatched.add(name);
            }
        }
        return new BatchMatchResultVO().setMatched(matched).setUnmatched(unmatched);
    }

    /** 条目名列表里是否有任一名字包含类型中文名 */
    private boolean matchesAnyName(List<String> names, String label) {
        for (String name : names) {
            if (name.contains(label)) {
                return true;
            }
        }
        return false;
    }

    /** 条目名是否命中任一要求（包含该要求的类型中文名即算） */
    private boolean matchesAnyRequirement(String name, List<MaterialRequirement> reqs) {
        for (MaterialRequirement req : reqs) {
            if (name.contains(MaterialTypeEnum.labelOf(req.getMaterialType()))) {
                return true;
            }
        }
        return false;
    }
}
