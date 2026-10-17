package com.viomanager.semester.support;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.semester.entity.Semester;
import com.viomanager.semester.mapper.SemesterMapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学期切换执行引擎（A 线清单 §5 点名归骨架：S08 的数据搬运）。
 * 独立成类而不是塞进 service：execute 的编排方法不能整体 @Transactional——
 * 占位 RUNNING 与失败回写 FAILED 必须各自独立提交，否则引擎事务一回滚会把失败状态也吞掉。
 * 数据搬运全部条件更新：重复执行/并发执行自然被行数检查拦下。
 */
@Component
public class SemesterSwitchEngine {

    @Resource
    private SemesterMapper semesterMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 执行结果计数（record 只读载体） */
    public record SwitchCounts(int archivedContests, int inheritedTeams, int inheritedMembers) {
    }

    /**
     * 引擎主体：旧学期归档 → 新学期激活 → 已结束比赛归档、未结束比赛继承 → ACTIVE 赛队继承。
     * 成员关系挂在 team_member 上随赛队自动继承，仅计数回填（PRD §3.4）。
     */
    @Transactional
    public SwitchCounts doSwitch(Long fromId, Long toId) {
        LocalDateTime now = LocalDateTime.now();
        //1.旧学期归档：条件更新带 is_current=1，非当前学期兜不住（并发/脏数据）时 0 行报状态冲突
        int archived = semesterMapper.update(null, new UpdateWrapper<Semester>()
                .eq("id", fromId).eq("is_current", 1)
                .set("status", "ARCHIVED").set("archived_at", now).set("is_current", 0));
        if (archived == 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "当前学期状态已变化，请刷新重试");
        }
        //2.新学期激活为当前
        semesterMapper.update(null, new UpdateWrapper<Semester>()
                .eq("id", toId)
                .set("status", "ACTIVE").set("is_current", 1));
        //3.比赛分流：已结束 FINISHED 归档；未结束（非 FINISHED/ARCHIVED）继承到新学期
        int archivedContests = jdbcTemplate.update(
                "UPDATE contest SET status = 'ARCHIVED'"
                        + " WHERE semester_id = ? AND status = 'FINISHED' AND deleted = 0", fromId);
        jdbcTemplate.update(
                "UPDATE contest SET semester_id = ?"
                        + " WHERE semester_id = ? AND status NOT IN ('FINISHED', 'ARCHIVED') AND deleted = 0",
                toId, fromId);
        //4.赛队继承：ACTIVE 赛队挂到新学期
        List<Long> teamIds = jdbcTemplate.queryForList(
                "SELECT id FROM team WHERE semester_id = ? AND status = 'ACTIVE' AND deleted = 0",
                Long.class, fromId);
        if (!teamIds.isEmpty()) {
            String placeholders = teamIds.stream().map(i -> "?").collect(Collectors.joining(","));
            Object[] args = new Object[teamIds.size() + 1];
            args[0] = toId;
            for (int i = 0; i < teamIds.size(); i++) {
                args[i + 1] = teamIds.get(i);
            }
            jdbcTemplate.update("UPDATE team SET semester_id = ? WHERE id IN (" + placeholders + ")", args);
        }
        //5.成员关系数（随队自动继承，仅计数）
        int memberCount = 0;
        if (!teamIds.isEmpty()) {
            String placeholders = teamIds.stream().map(i -> "?").collect(Collectors.joining(","));
            Integer counted = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM team_member WHERE deleted = 0 AND team_id IN (" + placeholders + ")",
                    Integer.class, teamIds.toArray());
            memberCount = counted == null ? 0 : counted;
        }
        return new SwitchCounts(archivedContests, teamIds.size(), memberCount);
    }
}
