package com.viomanager.semester.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.MaintenanceLock;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.semester.dto.SwitchTaskCreateDTO;
import com.viomanager.semester.entity.Semester;
import com.viomanager.semester.entity.SemesterSwitchTask;
import com.viomanager.semester.mapper.SemesterMapper;
import com.viomanager.semester.mapper.SemesterSwitchTaskMapper;
import com.viomanager.semester.service.SwitchTaskService;
import com.viomanager.semester.support.SemesterSwitchEngine;
import com.viomanager.semester.support.SemesterSwitchEngine.SwitchCounts;
import com.viomanager.semester.vo.SwitchTaskVO;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 切换任务实现（A 线契约 S05-S08）。
 * execute 刻意不挂 @Transactional：占位 RUNNING、引擎搬运、失败回写是三段独立提交——
 * 引擎事务回滚时失败状态必须落得住，否则任务永远卡 PENDING_EXECUTE。
 */
@Slf4j
@Service
public class SwitchTaskServiceImpl implements SwitchTaskService {

    @Resource
    private SemesterMapper semesterMapper;

    @Resource
    private SemesterSwitchTaskMapper switchTaskMapper;

    @Resource
    private SemesterSwitchEngine switchEngine;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private ObjectMapper objectMapper;

    /** S05 发起：密码二次确认 → 校验双方学期 → 拍快照 → 落任务进 30 分钟窗口 */
    @Override
    public Long create(SwitchTaskCreateDTO dto) {
        CurrentUser user = UserHolder.getUser();
        //1.超管密码二次确认（比对当前登录人自己的密码，错则 1001 不区分细节）
        String hash = jdbcTemplate.queryForObject(
                "SELECT password FROM sys_user WHERE id = ? AND deleted = 0", String.class, user.getUserId());
        if (hash == null || !passwordEncoder.matches(dto.getPassword(), hash)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "二次确认密码不正确");
        }
        //2.源学期 = 当前学期（全平台唯一 is_current=1）
        Semester from = semesterMapper.selectOne(new QueryWrapper<Semester>()
                .eq("is_current", 1).eq("deleted", 0).last("limit 1"));
        if (from == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "当前学期不存在，无法发起切换");
        }
        //3.目标学期校验：存在、不是当前学期、未归档
        Semester to = semesterMapper.selectById(dto.getToSemesterId());
        if (to == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "目标学期不存在");
        }
        if (to.getId().equals(from.getId())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "目标学期不能是当前学期");
        }
        if ("ARCHIVED".equals(to.getStatus())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "目标学期已归档，不可切换");
        }
        //4.同一时刻只允许一个待执行任务
        Long pending = switchTaskMapper.selectCount(new QueryWrapper<SemesterSwitchTask>()
                .eq("status", "PENDING_EXECUTE").eq("deleted", 0));
        if (pending != null && pending > 0) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "已有待执行的切换任务，请先执行或取消");
        }
        //5.拍影响快照：待归档比赛（FINISHED）/ 待继承赛队（ACTIVE）/ 其成员关系数
        SwitchTaskVO.Impact impact = snapshotImpact(from.getId());
        //6.落任务：execute_at = 创建时刻 + 30 分钟 = 窗口截止
        SemesterSwitchTask task = new SemesterSwitchTask();
        task.setFromSemesterId(from.getId());
        task.setFromSemesterName(from.getName());
        task.setToSemesterId(to.getId());
        task.setToSemesterName(to.getName());
        task.setStatus("PENDING_EXECUTE");
        task.setWindowMinutes(30);
        task.setExecuteAt(LocalDateTime.now().plusMinutes(30));
        task.setImpactSnapshot(toJson(impact));
        task.setArchivedContests(0);
        task.setInheritedTeams(0);
        task.setInheritedMembers(0);
        task.setVersion(0);
        task.setInitiatedBy(user.getUserId());
        task.setInitiatedByName(user.getRealName());
        switchTaskMapper.insert(task);
        return task.getId();
    }

    /** S06 列表：新在前，快照 JSON 解析坏了个别行不炸整页 */
    @Override
    public List<SwitchTaskVO> list() {
        List<SemesterSwitchTask> tasks = switchTaskMapper.selectList(
                new QueryWrapper<SemesterSwitchTask>().orderByDesc("id"));
        return tasks.stream().map(this::toVO).toList();
    }

    /** S07 取消：条件更新（PENDING_EXECUTE → CANCELLED），0 行即并发已处理 */
    @Override
    public void cancel(Long id) {
        requireTask(id);
        int rows = switchTaskMapper.update(null, new UpdateWrapper<SemesterSwitchTask>()
                .eq("id", id).eq("status", "PENDING_EXECUTE")
                .set("status", "CANCELLED")
                .set("cancelled_by", UserHolder.getUser().getUserId())
                .set("cancelled_at", LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "任务不在待执行状态，无法取消");
        }
    }

    /** S08 执行：窗口校验 → 乐观锁占位 → 维护锁 → 引擎 → 成败收尾（finally 必解锁） */
    @Override
    public void execute(Long id) {
        SemesterSwitchTask task = requireTask(id);
        //1.只有待执行状态可执行
        if (!"PENDING_EXECUTE".equals(task.getStatus())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "任务不在待执行状态");
        }
        //2.窗口校验：过截止时刻拒绝（保持 PENDING_EXECUTE，由发起人重新发起）
        if (task.getExecuteAt() != null && LocalDateTime.now().isAfter(task.getExecuteAt())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "执行窗口已过期，请重新发起切换任务");
        }
        //3.乐观锁占位 RUNNING（PENDING_EXECUTE + version 双条件），0 行 = 已被并发抢跑
        int claimed = switchTaskMapper.update(null, new UpdateWrapper<SemesterSwitchTask>()
                .eq("id", id).eq("status", "PENDING_EXECUTE").eq("version", task.getVersion())
                .set("status", "RUNNING")
                .set("started_at", LocalDateTime.now())
                .set("version", task.getVersion() + 1));
        if (claimed == 0) {
            throw new BizException(CommonErrorCode.CONCURRENT_MODIFIED);
        }
        //4.维护锁：全员禁访（超管除外），引擎结束必释放
        MaintenanceLock.lock();
        try {
            //5.引擎搬运（独立事务：数据失败整体回滚）
            SwitchCounts counts = switchEngine.doSwitch(task.getFromSemesterId(), task.getToSemesterId());
            //6.成功收尾（单条条件更新独立提交）
            switchTaskMapper.update(null, new UpdateWrapper<SemesterSwitchTask>()
                    .eq("id", id).eq("status", "RUNNING")
                    .set("status", "SUCCESS")
                    .set("archived_contests", counts.archivedContests())
                    .set("inherited_teams", counts.inheritedTeams())
                    .set("inherited_members", counts.inheritedMembers())
                    .set("finished_at", LocalDateTime.now()));
        } catch (Exception e) {
            //7.失败收尾：引擎事务已回滚，这里只落失败状态与原因（独立提交，必须成功）
            log.error("学期切换执行失败，任务 {}：{}", id, e.getMessage(), e);
            switchTaskMapper.update(null, new UpdateWrapper<SemesterSwitchTask>()
                    .eq("id", id).eq("status", "RUNNING")
                    .set("status", "FAILED")
                    .set("error_message", truncate(e.getMessage()))
                    .set("finished_at", LocalDateTime.now()));
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "学期切换执行失败，数据已回滚，请查看任务失败原因");
        } finally {
            MaintenanceLock.unlock();
        }
    }

    // ---------- 私有支撑 ----------

    /** 拍影响快照：三个计数一条 SQL 不如三条清晰（量级小，可读优先） */
    private SwitchTaskVO.Impact snapshotImpact(Long fromSemesterId) {
        SwitchTaskVO.Impact impact = new SwitchTaskVO.Impact();
        Integer contests = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contest WHERE semester_id = ? AND status = 'FINISHED' AND deleted = 0",
                Integer.class, fromSemesterId);
        Integer teams = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team WHERE semester_id = ? AND status = 'ACTIVE' AND deleted = 0",
                Integer.class, fromSemesterId);
        Integer members = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE deleted = 0 AND team_id IN"
                        + " (SELECT id FROM team WHERE semester_id = ? AND status = 'ACTIVE' AND deleted = 0)",
                Integer.class, fromSemesterId);
        impact.setContestCount(contests == null ? 0 : contests);
        impact.setTeamCount(teams == null ? 0 : teams);
        impact.setMemberRelationCount(members == null ? 0 : members);
        return impact;
    }

    private SemesterSwitchTask requireTask(Long id) {
        SemesterSwitchTask task = switchTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "切换任务不存在");
        }
        return task;
    }

    private SwitchTaskVO toVO(SemesterSwitchTask task) {
        SwitchTaskVO vo = new SwitchTaskVO();
        vo.setId(String.valueOf(task.getId()));
        vo.setFromSemesterName(task.getFromSemesterName());
        vo.setToSemesterName(task.getToSemesterName());
        vo.setStatus(task.getStatus());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setDeadline(task.getExecuteAt());
        vo.setInitiatedByName(task.getInitiatedByName());
        vo.setVersion(task.getVersion());
        vo.setArchivedContests(task.getArchivedContests());
        vo.setInheritedTeams(task.getInheritedTeams());
        vo.setInheritedMembers(task.getInheritedMembers());
        vo.setErrorMessage(task.getErrorMessage());
        if (task.getImpactSnapshot() != null && !task.getImpactSnapshot().isBlank()) {
            try {
                vo.setImpact(objectMapper.readValue(task.getImpactSnapshot(), SwitchTaskVO.Impact.class));
            } catch (Exception e) {
                log.warn("切换任务 {} 快照 JSON 解析失败，忽略", task.getId());
            }
        }
        return vo;
    }

    private String toJson(SwitchTaskVO.Impact impact) {
        try {
            return objectMapper.writeValueAsString(impact);
        } catch (Exception e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "快照序列化失败");
        }
    }

    /** 失败原因截断入库（TEXT 列，防整段堆栈灌库） */
    private String truncate(String message) {
        if (message == null) {
            return "未知原因";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
