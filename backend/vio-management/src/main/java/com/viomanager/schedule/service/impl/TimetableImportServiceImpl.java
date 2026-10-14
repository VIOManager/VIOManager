package com.viomanager.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ScheduleErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.file.service.IFileService;
import com.viomanager.file.vo.FileUploadVO;
import com.viomanager.schedule.entity.TimetableImport;
import com.viomanager.schedule.mapper.TimetableImportMapper;
import com.viomanager.schedule.service.TimetableImportService;
import com.viomanager.schedule.support.SemesterSupport;
import com.viomanager.schedule.support.TimetableExcelSupport;
import com.viomanager.schedule.support.TimetableWriteSupport;
import com.viomanager.schedule.vo.ImportBatchVO;
import com.viomanager.schedule.vo.ImportResultVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 课表导入实现（TT01–TT05）。
 * 写库链路（TT02 命中学期 / TT05 续跑共用）拆到 TimetableWriteSupport（300 行铁律）：
 * 课表行 → course_timetable；班级去重 → class_mapping（confirmed=0，等 TT07 确认）。
 * 学期归一化失败时课表行不落库（等 TT05 指定学期后续跑），批次留 PENDING。
 */
@Service
public class TimetableImportServiceImpl implements TimetableImportService {

    @Resource
    private TimetableImportMapper importMapper;

    @Resource
    private TimetableExcelSupport excelSupport;

    @Resource
    private TimetableWriteSupport writeSupport;

    @Resource
    private SemesterSupport semesterSupport;

    @Resource
    private IFileService fileService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        byte[] bytes = excelSupport.buildTemplate();
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\""
                    + java.net.URLEncoder.encode("课表导入模板.xlsx", java.nio.charset.StandardCharsets.UTF_8) + "\"");
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "文件下载输出失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO upload(MultipartFile file) {
        CurrentUser user = UserHolder.getUser();
        //1.原始文件先走骨架文件服务（MinIO + 病毒扫描），TT05 续跑要重读它
        Result<FileUploadVO> uploaded = fileService.upload(file, "TIMETABLE_IMPORT", null);
        if (uploaded.getCode() != Result.SUCCESS_CODE) {
            throw new BizException(uploaded.getCode(), uploaded.getMessage());
        }
        //2.同步解析（拍板②：学校课表量小够用）
        TimetableExcelSupport.ParsedTimetable parsed = parseFile(file);
        int total = parsed.rows().size() + parsed.errors().size();
        if (total == 0) {
            throw new BizException(ScheduleErrorCode.PARSE_FAILED, "文件无有效数据行");
        }
        //3.建批次：归一化命中直接写课表转 MAPPING；零/多命中留 PENDING（8002 语义）
        TimetableImport batch = new TimetableImport()
                .setFileId(uploaded.getData().getFileId())
                .setFileName(file.getOriginalFilename())
                .setOrgId(user.getOrgId())
                .setSemesterRawName(parsed.semesterRawName())
                .setTotalRows(total)
                .setSuccessRows(parsed.rows().size())
                .setFailedRows(parsed.errors().size())
                .setErrorDetail(toErrorJson(parsed.errors()))
                .setImportedBy(user.getUserId())
                .setImportedAt(LocalDateTime.now())
                .setStatus("PENDING");
        Long semesterId = semesterSupport.matchByRawName(parsed.semesterRawName());
        if (semesterId != null) {
            batch.setSemesterId(semesterId);
            importMapper.insert(batch);
            writeSupport.writeParsed(batch, semesterId, parsed);
        } else {
            importMapper.insert(batch);
        }
        //4.全部行解析失败 → FAILED
        if (parsed.rows().isEmpty() && !parsed.errors().isEmpty()) {
            batch.setStatus("FAILED");
            importMapper.updateById(batch);
        }
        ImportResultVO result = new ImportResultVO();
        result.setBatchId(batch.getId());
        result.setTotalCount(total);
        result.setSuccessCount(parsed.rows().size());
        result.setFailCount(parsed.errors().size());
        return result;
    }

    @Override
    public List<ImportBatchVO> list(Long semesterId) {
        List<TimetableImport> rows = importMapper.selectList(new LambdaQueryWrapper<TimetableImport>()
                .eq(semesterId != null, TimetableImport::getSemesterId, semesterId)
                .orderByDesc(TimetableImport::getImportedAt));
        Map<Long, String> semesterNames = semesterNames();
        List<ImportBatchVO> result = new ArrayList<>();
        rows.forEach(row -> {
            ImportBatchVO vo = toVO(row, semesterNames);
            vo.setErrors(List.of());
            result.add(vo);
        });
        return result;
    }

    @Override
    public ImportBatchVO detail(Long id) {
        TimetableImport batch = requireBatch(id);
        ImportBatchVO vo = toVO(batch, semesterNames());
        vo.setErrors(fromErrorJson(batch.getErrorDetail()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignSemester(Long id, Long semesterId) {
        TimetableImport batch = requireBatch(id);
        if (!"PENDING".equals(batch.getStatus())) {
            throw new BizException(ScheduleErrorCode.IMPORT_STATUS_NOT_ALLOWED, "仅待归一化批次可指定学期");
        }
        if (batch.getSemesterId() != null) {
            throw new BizException(ScheduleErrorCode.IMPORT_STATUS_NOT_ALLOWED, "批次已关联学期，不可重复指定");
        }
        semesterSupport.requireSemester(semesterId);
        if (batch.getFileId() == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "批次未存储原始文件，无法续跑解析");
        }
        //重读存储的原始文件续跑解析（拍板②同步口径）
        byte[] bytes;
        try (InputStream in = fileService.openCheckedStream(batch.getFileId())) {
            bytes = in.readAllBytes();
        } catch (Exception e) {
            throw new BizException(ScheduleErrorCode.PARSE_FAILED, "课表文件读取失败：" + e.getMessage());
        }
        TimetableExcelSupport.ParsedTimetable parsed = excelSupport.parse(bytes);
        batch.setSemesterId(semesterId)
                .setTotalRows(parsed.rows().size() + parsed.errors().size())
                .setSuccessRows(parsed.rows().size())
                .setFailedRows(parsed.errors().size())
                .setErrorDetail(toErrorJson(parsed.errors()))
                .setStatus(parsed.rows().isEmpty() && !parsed.errors().isEmpty() ? "FAILED" : "MAPPING");
        importMapper.updateById(batch);
        if (!parsed.rows().isEmpty()) {
            writeSupport.writeParsed(batch, semesterId, parsed);
        }
    }

    private TimetableExcelSupport.ParsedTimetable parseFile(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return excelSupport.parse(in);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ScheduleErrorCode.PARSE_FAILED, e.getMessage());
        }
    }

    private TimetableImport requireBatch(Long id) {
        TimetableImport batch = importMapper.selectById(id);
        if (batch == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "导入批次不存在");
        }
        return batch;
    }

    private ImportBatchVO toVO(TimetableImport batch, Map<Long, String> semesterNames) {
        ImportBatchVO vo = new ImportBatchVO();
        vo.setId(batch.getId());
        vo.setSemesterId(batch.getSemesterId());
        vo.setSemesterName(semesterNames.get(batch.getSemesterId()));
        vo.setStatus(batch.getStatus());
        vo.setTotalCount(batch.getTotalRows());
        vo.setSuccessCount(batch.getSuccessRows());
        vo.setFailCount(batch.getFailedRows());
        vo.setCreatedAt(batch.getImportedAt());
        return vo;
    }

    private Map<Long, String> semesterNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        semesterSupport.all().forEach(row -> names.put(row.id(), row.name()));
        return names;
    }

    private String toErrorJson(List<TimetableExcelSupport.RowError> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<ImportBatchVO.ErrorLine> fromErrorJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, ImportBatchVO.ErrorLine.class));
        } catch (Exception e) {
            return List.of();
        }
    }
}
