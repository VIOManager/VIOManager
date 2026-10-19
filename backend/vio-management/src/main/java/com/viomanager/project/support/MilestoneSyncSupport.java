package com.viomanager.project.support;

import jakarta.annotation.Resource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 节点→任务同步（方案 §7.7 并发矩阵的行锁串行化项）。
 *
 * 双入口：
 *  1. 审批通过回调 syncOnApproved——给挂载该比赛的**所有** ACTIVE 项目批量建任务，置 synced=1
 *  2. 挂载回溯 syncOnAttach（PR13 调用）——给**这一个**项目补建该比赛下它缺的任务
 *
 * 防重口径（数据库冻结、无 uk 可用，全靠锁内查询）：
 *  - 两个入口都先 SELECT ... FOR UPDATE 锁定节点行，同一节点的同步天然串行；
 *    多节点回溯按 id 升序加锁，防交叉死锁
 *  - 回调入口锁内看 synced 标志（防审批重放/并发双回调）
 *  - 回溯入口锁内逐节点查"该项目已有该节点任务"（synced=1 只说明审批时同步过，
 *    之后新挂载的项目仍需补建——标志不能替代存在性检查）
 *  - 批量建任务用多值 INSERT（方案 §7.8），一次往返
 */
@Slf4j
@Component
public class MilestoneSyncSupport {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 审批通过后的同步：给挂载该比赛的每个 ACTIVE 项目建一条任务
     * （title=节点名、due_date=节点时间、milestone_node_id 回填）并置 synced=1。
     * 幂等：锁内 synced=1 直接返回（审批单重放/双回调场景）。
     */
    public void syncOnApproved(Long nodeId) {
        //1.锁节点行：与挂载回溯入口互斥（同一节点的建任务动作串行化）
        Map<String, Object> node = lockNode(nodeId);
        if (node == null) {
            return;
        }
        //2.锁内复查 synced：已同步过直接返回（至多一次）
        if (Integer.valueOf(1).equals(node.get("synced"))) {
            log.info("节点 {} 已同步过，跳过（回调重放/并发防护）", nodeId);
            return;
        }
        Long contestId = ((Number) node.get("contest_id")).longValue();
        String title = (String) node.get("name");
        LocalDateTime dueDate = toLocalDateTime(node.get("node_time"));
        //3.挂载该比赛的 ACTIVE 项目（项目本身未删且 ACTIVE——已结束/归档项目不再追任务）
        List<Long> projectIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT r.project_id FROM team_project_ref r "
                        + "JOIN project p ON p.id = r.project_id AND p.deleted = 0 AND p.status = 'ACTIVE' "
                        + "WHERE r.contest_id = ? AND r.status = 'ACTIVE' AND r.deleted = 0",
                Long.class, contestId);
        if (!projectIds.isEmpty()) {
            batchInsertTasks(projectIds, nodeId, title, dueDate);
        }
        //4.置 synced（updated_at 自赋值——审核时间戳列已由回调方落库，不让批量动作污染它）
        jdbcTemplate.update(
                "UPDATE milestone_node SET synced = 1, synced_at = NOW(), updated_at = updated_at "
                        + "WHERE id = ?", nodeId);
        log.info("节点 {} 审核通过同步完成：{} 个项目各建一条任务", nodeId, projectIds.size());
    }

    /**
     * 挂载回溯（PR13）：项目刚挂到某比赛，补建该比赛下它缺的已审核节点任务。
     * 节点按 id 升序逐个加锁（与其他入口的多锁顺序一致，防死锁）。
     */
    public void syncOnAttach(Long projectId, Long contestId) {
        List<Long> nodeIds = jdbcTemplate.queryForList(
                "SELECT id FROM milestone_node WHERE contest_id = ? AND status = 'APPROVED' "
                        + "AND deleted = 0 ORDER BY id ASC", Long.class, contestId);
        for (Long nodeId : nodeIds) {
            Map<String, Object> node = lockNode(nodeId);
            if (node == null) {
                continue;
            }
            //锁内存在性检查：该项目已有该节点的任务则跳过（审批时同步过 / 已回溯过）。
            //当前读（FOR UPDATE）：普通 COUNT 是快照读，与并发的回调同步会互相看不到
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM project_task WHERE project_id = ? AND milestone_node_id = ? "
                            + "AND deleted = 0 FOR UPDATE",
                    Integer.class, projectId, nodeId);
            if (existing != null && existing > 0) {
                continue;
            }
            batchInsertTasks(List.of(projectId), nodeId,
                    (String) node.get("name"), toLocalDateTime(node.get("node_time")));
        }
    }

    /** SELECT ... FOR UPDATE 锁定节点行并返回行数据（不存在返回 null——节点被删则无事可做） */
    private Map<String, Object> lockNode(Long nodeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, contest_id, name, node_time, synced FROM milestone_node "
                        + "WHERE id = ? AND deleted = 0 FOR UPDATE", nodeId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 多值 INSERT 批量建任务（一条 SQL 一次往返；雪花 ID 逐个生成） */
    private void batchInsertTasks(List<Long> projectIds, Long nodeId, String title,
                                   LocalDateTime dueDate) {
        //7 列：id, project_id, milestone_node_id, title, due_date, status, sort_no
        String values = String.join(", ",
                projectIds.stream().map(pid -> "(?, ?, ?, ?, ?, 'TODO', 0)").toList());
        String sql = "INSERT INTO project_task (id, project_id, milestone_node_id, title, due_date, "
                + "status, sort_no) VALUES " + values;
        jdbcTemplate.update(sql, projectIds.stream().flatMap(pid -> java.util.stream.Stream.of(
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), pid, nodeId, title,
                dueDate == null ? null : Timestamp.valueOf(dueDate))).toArray());
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return null;
    }
}
