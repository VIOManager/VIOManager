package com.viomanager.kb.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * KB08 评论项（时间正序，含楼中楼：顶层评论带 children，子回复折叠在其下）。
 * 父级 HIDDEN/删除时整楼不返回（查询侧折叠，见 service）。
 */
@Data
public class KbCommentVO {

    private Long id;

    /** 评论人 ID（前端判断作者用 mine 即可，不必展示） */
    private Long memberId;

    /** 评论人姓名（发表时快照） */
    private String memberName;

    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 当前登录用户是否作者（删除按钮显隐用；后端仍校验） */
    private Boolean mine;

    /** 楼中楼子回复（时间正序） */
    private List<KbCommentVO> children = new ArrayList<>();
}
