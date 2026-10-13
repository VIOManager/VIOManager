package com.viomanager.achievement.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 成果详情出参（A02）= AchievementVO + 描述 / 佐证 / 创建时间 / 队长视角标识。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AchievementDetailVO extends AchievementVO {

    /** 成果描述 */
    private String description;

    /** 佐证文件 ID（当前生效的一份，前端只读这个） */
    private Long evidenceFileId;

    /** 佐证文件名 */
    private String evidenceFileName;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 当前登录人是否该队队长 */
    private boolean viewerIsLeader;
}
