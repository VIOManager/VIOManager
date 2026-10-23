package com.viomanager.schedule.support;

import com.viomanager.common.enums.ErrorCode;
import com.viomanager.common.exception.BizException;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 学期读取支撑（A 线 semester 表，JdbcTemplate 直读，A 线交付后联调评估上提）。
 * 排班与绩效共用：教学周数校验（8010/8047）、当前学期默认（PF05）、TT02 学期名归一化匹配。
 */
@Component
public class SemesterSupport {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 学期只读行（显式列名，禁 SELECT *）。 */
    public record SemesterRow(Long id, String name, Integer totalWeeks, String status, Integer isCurrent) {
    }

    /** 按 id 读学期，不存在/已删抛 1004。 */
    public SemesterRow requireSemester(Long semesterId) {
        List<SemesterRow> rows = jdbcTemplate.query(
                "SELECT id, name, total_weeks, status, is_current FROM semester WHERE id = ? AND deleted = 0",
                (rs, i) -> new SemesterRow(rs.getLong(1), rs.getString(2), rs.getInt(3),
                        rs.getString(4), rs.getInt(5)),
                semesterId);
        if (rows.isEmpty()) {
            throw new BizException(com.viomanager.common.enums.CommonErrorCode.NOT_FOUND, "学期不存在");
        }
        return rows.get(0);
    }

    /** 当前激活学期（is_current=1 且 ACTIVE），无激活学期返回 null（PF05 由调用方决定兜底文案）。 */
    public SemesterRow currentActive() {
        List<SemesterRow> rows = jdbcTemplate.query(
                "SELECT id, name, total_weeks, status, is_current FROM semester "
                        + "WHERE is_current = 1 AND status = 'ACTIVE' AND deleted = 0 LIMIT 1",
                (rs, i) -> new SemesterRow(rs.getLong(1), rs.getString(2), rs.getInt(3),
                        rs.getString(4), rs.getInt(5)));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 全部未删学期（TT02 归一化匹配用）。 */
    public List<SemesterRow> all() {
        return jdbcTemplate.query(
                "SELECT id, name, total_weeks, status, is_current FROM semester WHERE deleted = 0",
                (rs, i) -> new SemesterRow(rs.getLong(1), rs.getString(2), rs.getInt(3),
                        rs.getString(4), rs.getInt(5)));
    }

    /**
     * 学期名归一化匹配：先精确名匹配，再归一化键匹配；唯一命中返回学期 ID，
     * 零命中或多命中返回 null（TT02 留 PENDING，8002 语义）。
     */
    public Long matchByRawName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        List<SemesterRow> all = all();
        for (SemesterRow row : all) {
            if (rawName.equals(row.name())) {
                return row.id();
            }
        }
        String key = NameNormalizer.semesterKey(rawName);
        List<SemesterRow> hits = all.stream()
                .filter(r -> key.equals(NameNormalizer.semesterKey(r.name())))
                .toList();
        return hits.size() == 1 ? hits.get(0).id() : null;
    }

    /** 周次教学范围校验：semester 必须存在、weekNo 在 1..totalWeeks 内，越界抛调用方给定的错误码（8010/8047）。 */
    public void checkWeekInRange(Long semesterId, Integer weekNo, ErrorCode outOfRange) {
        SemesterRow row = requireSemester(semesterId);
        if (weekNo == null || weekNo < 1 || weekNo > row.totalWeeks()) {
            throw new BizException(outOfRange,
                    "周次须在 1–" + row.totalWeeks() + " 内，当前传入：" + weekNo);
        }
    }
}
