package com.viomanager.material.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.enums.MaterialTypeEnum;
import com.viomanager.file.service.IFileService;
import com.viomanager.material.dto.MaterialBatchUploadDTO;
import com.viomanager.material.entity.MaterialRequirement;
import com.viomanager.material.entity.MaterialSubmission;
import com.viomanager.material.entity.MaterialTask;
import com.viomanager.material.mapper.MaterialRequirementMapper;
import com.viomanager.material.mapper.MaterialSubmissionMapper;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamRow;
import com.viomanager.material.vo.BatchMatchResultVO;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * zip 批量上传支撑（MS03）：解压读条目 + 两层匹配 + 未交要求的落库。
 * 匹配规则：名字（zip 条目名 / 兜底的压缩包名）包含要求类型的中文名（MaterialTypeEnum.labelOf）。
 *
 * ⚠️ 两层匹配（C 线接口清单 §3 MS03）：
 *  - 第一层（优先）：经文件服务真实解压读出全部条目名再逐条匹配。当前 MinIO 对象存储不可用
 *    （官方已停止分发镜像，见项目内待办记录），该路径调用即抛异常，catch 后降级第二层，绝不让接口 500。
 *  - 第二层（兜底）：拿压缩包名本身当唯一"条目名"做包含匹配，如"报名表+项目书.zip"能匹配到
 *    报名表、项目书两条要求；一个要求都没匹配上时，unmatched 就是压缩包名本身。
 *
 * ⚠️ 本次因对象存储不可用，"真实解压"路径未经实测，走的是兜底匹配；
 *    待存储方案恢复后需回归验证真实解压路径。
 */
// TODO(联调) 存储方案恢复后回归验证第一层真实解压路径
@Slf4j
@Component
public class MaterialZipBatchSupport {

    @Resource
    private IFileService fileService;

    @Resource
    private MaterialRequirementMapper requirementMapper;

    @Resource
    private MaterialSubmissionMapper submissionMapper;

    /**
     * 两层匹配并落库（在调用方的事务内执行）。入参校验已由调用方按 MS02 同口径 6 步完成。
     * 已有 is_current=1 提交的要求跳过不重复覆盖；未交的插入合成提交记录
     * （fileName = 队名-类型中文名.pdf、fileSize=0、多条要求共用压缩包 fileId）。
     * 匹配到的要求（含已交过被跳过的）记入 matched；没匹配到任何要求的条目名记入 unmatched。
     */
    public BatchMatchResultVO matchAndInsert(MaterialTask task, TeamRow team, Long memberId,
                                             String realName, MaterialBatchUploadDTO dto) {
        //1.第一层：真实解压读条目名；失败（存储不可用等）降级为压缩包名单条目，绝不让接口 500
        List<String> names;
        try {
            names = readZipEntryNames(dto.getFileId());
        } catch (Exception e) {
            log.warn("zip 真实解压失败（对象存储不可用？），降级为压缩包名匹配：{}", e.getMessage());
            names = null;
        }
        if (names == null) {
            names = List.of(dto.getFileName());
        }
        //2.任务下全部未删要求（sort_no / id 升序）+ 该队已有当前版的要求集（已交跳过的判定依据）
        List<MaterialRequirement> reqs = requirementMapper.selectList(
                new QueryWrapper<MaterialRequirement>().eq("task_id", dto.getTaskId())
                        .orderByAsc("sort_no").orderByAsc("id"));
        Set<Long> submittedReqIds = new HashSet<>(
                submissionMapper.selectCurrentRequirementIds(dto.getTeamId()));
        //3.逐要求匹配：命中即记 matched；未交过的落库（versionNo 取历史最大 +1，无历史即为 1）
        List<BatchMatchResultVO.MatchedItem> matched = new ArrayList<>();
        for (MaterialRequirement req : reqs) {
            String label = MaterialTypeEnum.labelOf(req.getMaterialType());
            if (names.stream().noneMatch(name -> name.contains(label))) {
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
            submission.setOrgId(team.orgId());
            submission.setFileId(dto.getFileId());
            submission.setFileName(team.name() + "-" + label + ".pdf");
            submission.setFileSize(0L);
            submission.setIsCurrent(1);
            submission.setVersionNo((maxVersion == null ? 0 : maxVersion) + 1);
            submission.setSubmittedBy(memberId);
            submission.setSubmittedByName(realName);
            submission.setSourceZipName(dto.getFileName());
            //submittedAt 必须手工赋值：AuditMetaObjectHandler 只填四个审计列，不管业务时间列
            submission.setSubmittedAt(LocalDateTime.now());
            submissionMapper.insert(submission);
        }
        //4.没匹配到任何要求的条目名 → unmatched（兜底层里就是压缩包名本身）
        List<String> unmatched = names.stream()
                .filter(name -> reqs.stream().noneMatch(
                        req -> name.contains(MaterialTypeEnum.labelOf(req.getMaterialType()))))
                .toList();
        return new BatchMatchResultVO().setMatched(matched).setUnmatched(unmatched);
    }

    /**
     * 第一层：经文件服务读 zip 内容并解出全部条目名（只取名，不读内容）。
     * 文件服务唯一的读 API 是 download(fileId, response)，这里用动态代理造一个
     * "只认 getOutputStream"的假响应把回写字节截进内存，其余方法一律空操作；
     * 任何异常（含对象存储不可用）由调用方 catch 后降级。
     */
    private List<String> readZipEntryNames(Long fileId) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ServletOutputStream capture = new CaptureOutputStream(buffer);
        HttpServletResponse fakeResponse = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> "getOutputStream".equals(method.getName())
                        ? capture
                        : method.getReturnType() == boolean.class ? Boolean.FALSE : null);
        fileService.download(fileId, fakeResponse);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            List<String> names = new ArrayList<>();
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory()) {
                    names.add(entry.getName());
                }
            }
            return names;
        }
    }

    /** 截流到内存的输出流（真实解压路径专用；同步回写，用不到异步监听） */
    private static class CaptureOutputStream extends ServletOutputStream {

        private final ByteArrayOutputStream buffer;

        private CaptureOutputStream(ByteArrayOutputStream buffer) {
            this.buffer = buffer;
        }

        @Override
        public void write(int b) {
            buffer.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            //同步回写，无监听可设
        }
    }
}
