package com.viomanager.semester.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.SemesterErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.semester.dto.SemesterSaveDTO;
import com.viomanager.semester.entity.Semester;
import com.viomanager.semester.mapper.SemesterMapper;
import com.viomanager.semester.service.SemesterService;
import com.viomanager.semester.vo.SemesterVO;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学期管理实现（A 线契约 S01-S04，仅超管）。
 * S04 的关联检查走 jdbcTemplate 跨表计数（contest/team/achievement/switch_task
 * 均非本包实体，跨线读表同审批/人员先例）；status 与 isCurrent 只归切换引擎管。
 */
@Service
public class SemesterServiceImpl implements SemesterService {

    @Resource
    private SemesterMapper semesterMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** S01 列表：雪花 id 递增即创建顺序，倒序=新学期在前 */
    @Override
    public List<SemesterVO> list() {
        List<Semester> semesters = semesterMapper.selectList(
                new QueryWrapper<Semester>().orderByDesc("id"));
        return semesters.stream().map(this::toVO).toList();
    }

    /** S02 创建：查重 3008，新建固定 PREPARING 非当前 */
    @Override
    @Transactional
    public Long create(SemesterSaveDTO dto) {
        assertNameAvailable(dto.getName(), null);
        Semester semester = new Semester();
        semester.setName(dto.getName());
        semester.setStartDate(dto.getStartDate());
        semester.setEndDate(dto.getEndDate());
        semester.setStatus("PREPARING");
        semester.setIsCurrent(0);
        semester.setTotalWeeks(dto.getTotalWeeks() == null ? 20 : dto.getTotalWeeks());
        semester.setRemark(dto.getRemark());
        semesterMapper.insert(semester);
        return semester.getId();
    }

    /** S03 编辑：仅基础信息；改名同样查重（排除自己） */
    @Override
    @Transactional
    public void update(Long id, SemesterSaveDTO dto) {
        Semester semester = requireSemester(id);
        assertNameAvailable(dto.getName(), id);
        semester.setName(dto.getName());
        semester.setStartDate(dto.getStartDate());
        semester.setEndDate(dto.getEndDate());
        semester.setTotalWeeks(dto.getTotalWeeks() == null ? 20 : dto.getTotalWeeks());
        semester.setRemark(dto.getRemark());
        semesterMapper.updateById(semester);
    }

    /** S04 删除：四类关联任一存在即 3009（清单口径：比赛/赛队/成果/切换任务） */
    @Override
    @Transactional
    public void delete(Long id) {
        requireSemester(id);
        Integer refCount = jdbcTemplate.queryForObject(
                "SELECT (SELECT COUNT(*) FROM contest WHERE semester_id = ? AND deleted = 0)"
                        + " + (SELECT COUNT(*) FROM team WHERE semester_id = ? AND deleted = 0)"
                        + " + (SELECT COUNT(*) FROM achievement WHERE semester_id = ? AND deleted = 0)"
                        + " + (SELECT COUNT(*) FROM semester_switch_task"
                        + "   WHERE (from_semester_id = ? OR to_semester_id = ?) AND deleted = 0)",
                Integer.class, id, id, id, id, id);
        if (refCount != null && refCount > 0) {
            throw new BizException(SemesterErrorCode.SEMESTER_HAS_BUSINESS);
        }
        semesterMapper.deleteById(id);
    }

    // ---------- 私有支撑 ----------

    /** 名称查重（excludeId 用于编辑排除自身；建表 uk_semester_name 兜底并发） */
    private void assertNameAvailable(String name, Long excludeId) {
        Long count = semesterMapper.selectCount(new QueryWrapper<Semester>()
                .eq("name", name).ne(excludeId != null, "id", excludeId));
        if (count != null && count > 0) {
            throw new BizException(SemesterErrorCode.SEMESTER_NAME_EXISTS);
        }
    }

    private Semester requireSemester(Long id) {
        Semester semester = semesterMapper.selectById(id);
        if (semester == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "学期不存在");
        }
        return semester;
    }

    private SemesterVO toVO(Semester semester) {
        SemesterVO vo = new SemesterVO();
        vo.setId(String.valueOf(semester.getId()));
        vo.setName(semester.getName());
        vo.setStartDate(semester.getStartDate());
        vo.setEndDate(semester.getEndDate());
        vo.setStatus(semester.getStatus());
        vo.setIsCurrent(semester.getIsCurrent() != null && semester.getIsCurrent() == 1);
        vo.setTotalWeeks(semester.getTotalWeeks());
        vo.setArchivedAt(semester.getArchivedAt());
        vo.setRemark(semester.getRemark());
        return vo;
    }
}
