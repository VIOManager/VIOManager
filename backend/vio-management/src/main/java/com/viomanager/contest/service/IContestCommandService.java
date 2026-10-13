package com.viomanager.contest.service;

import com.viomanager.contest.dto.ContestCreateDTO;
import com.viomanager.contest.dto.TrackCreateDTO;
import com.viomanager.contest.vo.ContestDetailVO;
import com.viomanager.contest.vo.TrackVO;

/**
 * 比赛写服务（C03–C09）。
 * 比赛状态机：DRAFT → PUBLISHED → REGISTERING → RUNNING → FINISHED → ARCHIVED。
 * 比赛管理不产生审批单（无回调 Handler），写操作即时生效。
 */
public interface IContestCommandService {

    /** C03 创建（sourceType=MANUAL，初始 status=DRAFT） */
    ContestDetailVO createContest(ContestCreateDTO dto);

    /** C04 编辑（非 ARCHIVED 才可编辑） */
    ContestDetailVO updateContest(Long id, ContestCreateDTO dto);

    /** C05 删除（仅 DRAFT 状态可删） */
    void deleteContest(Long id);

    /** C06 状态流转（4011 不可跳转；DRAFT→PUBLISHED 发布前检查 4001 逐项指出缺项） */
    void transitionStatus(Long id, String to);

    /** C07 添加赛道 */
    TrackVO createTrack(Long contestId, TrackCreateDTO dto);

    /** C08 编辑赛道（含赛道级规则覆盖） */
    TrackVO updateTrack(Long contestId, Long trackId, TrackCreateDTO dto);

    /** C09 删除赛道（有赛队挂载该赛道时拒绝） */
    void deleteTrack(Long contestId, Long trackId);
}
