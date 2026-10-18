package com.viomanager.semester.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 切换任务出参（S06 列表）。
 * deadline 对应表字段 execute_at（窗口截止时刻），字段名对齐前端 semester.ts 的 SwitchTask。
 */
@Data
public class SwitchTaskVO {

    private String id;

    private String fromSemesterName;

    private String toSemesterName;

    /** PENDING_EXECUTE / CANCELLED / RUNNING / SUCCESS / FAILED */
    private String status;

    private LocalDateTime createdAt;

    /** 窗口截止时刻，过期未执行不可再执行 */
    private LocalDateTime deadline;

    /** 发起人姓名 */
    private String initiatedByName;

    /** 影响快照（发起时拍板） */
    private Impact impact;

    /** 乐观锁版本号（前端执行时无需传，后端自查；透出供管理端展示） */
    private Integer version;

    /** 执行后回填：归档比赛数 */
    private Integer archivedContests;

    /** 执行后回填：继承赛队数 */
    private Integer inheritedTeams;

    /** 执行后回填：继承成员关系数 */
    private Integer inheritedMembers;

    /** 失败原因（FAILED 时有值） */
    private String errorMessage;

    /**
     * 影响快照：待归档比赛数 / 待继承赛队数 / 成员关系数。
     * 字段名对齐前端 SwitchTask.impact。
     */
    @Data
    public static class Impact {

        /** 待归档比赛数（已结束 FINISHED 的比赛） */
        private int contestCount;

        /** 待继承赛队数（ACTIVE 赛队） */
        private int teamCount;

        /** 待继承成员关系数（上述赛队的在册成员） */
        private int memberRelationCount;
    }
}
