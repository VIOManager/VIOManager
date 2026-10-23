package com.viomanager.schedule.service;

import com.viomanager.schedule.vo.ImportBatchVO;
import com.viomanager.schedule.vo.ImportResultVO;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 课表导入服务（TT01–TT05，接口清单 §2）。
 * 批次状态机：PENDING 待归一化 → MAPPING 待确认映射 → COMPLETED 全部确认；解析全失败 → FAILED。
 * 同步解析（拍板②：学校课表量小够用）。
 */
public interface TimetableImportService {

    /** TT01 模板下载：16 列 + 5 条示例行，xlsx 流式回写。 */
    void downloadTemplate(HttpServletResponse response);

    /** TT02 上传导入：存文件（走骨架文件服务）→ 同步解析 → 学期归一化（失败留 PENDING）→ 写课表与映射。 */
    ImportResultVO upload(MultipartFile file);

    /** TT03 批次列表，可按学期筛选。 */
    List<ImportBatchVO> list(Long semesterId);

    /** TT04 批次详情（含每行错误明细）。 */
    ImportBatchVO detail(Long id);

    /** TT05 归一化失败批次人工指定学期：仅 PENDING 且未关联学期（8003），指定后立即续跑解析。 */
    void assignSemester(Long id, Long semesterId);
}
