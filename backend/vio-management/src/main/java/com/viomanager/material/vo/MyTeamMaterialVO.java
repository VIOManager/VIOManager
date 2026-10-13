package com.viomanager.material.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 学生端"我的材料"出参（MS06）：我所在的每支队伍一项。
 * 只含"当前人在册（CONFIRMED/UNCONFIRMED）且 team.status=ACTIVE"的队伍，
 * 比赛未配材料任务的队伍已被后端跳过，不出现在数组里。
 */
@Data
public class MyTeamMaterialVO {

    /** 赛队 ID（JSON 序列化为字符串） */
    private Long teamId;

    /** 队名 */
    private String teamName;

    /** 该队所在比赛的材料任务摘要 */
    private TaskBrief task;

    /** 当前齐全率（0–100 整数，读 team.material_complete_rate） */
    private Integer completeRate;

    /** 必传项未交条数 */
    private Integer requiredMissing;

    /** 逐要求的提交状态（sort_no / id 升序，含完整要求对象） */
    private List<Item> items;

    /** 材料任务摘要；title 取 material_task.name（前端旧清单字段口径，见实体适配说明） */
    @Data
    @Accessors(chain = true)
    public static class TaskBrief {

        /** 任务 ID（JSON 序列化为字符串） */
        private Long id;

        /** 任务名称（material_task.name） */
        private String title;

        /** 截止时间，null = 不限期 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime deadline;
    }

    /** 单要求的提交状态：与 MS01 行相比，要求信息以完整对象透出（含 allowedFormats 数组与中文名） */
    @Data
    public static class Item {

        /** 完整要求对象（MaterialRequirementVO） */
        private MaterialRequirementVO requirement;

        /** 材料要求 ID */
        private Long requirementId;

        /** 提交状态：SUBMITTED 已有当前版 / NOT_SUBMITTED 未提交 */
        private String status;

        /** 当前版本号；未提交为 0 */
        private Integer version;

        /** 当前版文件名；未提交为空串 */
        private String fileName;

        /** 当前版提交时间（yyyy-MM-dd HH:mm:ss）；未提交为空串 */
        private String uploadedAt;
    }
}
