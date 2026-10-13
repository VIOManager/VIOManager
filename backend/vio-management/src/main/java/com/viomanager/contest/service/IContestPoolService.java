package com.viomanager.contest.service;

import com.viomanager.common.result.Result;
import com.viomanager.contest.dto.ContestCreateDTO;
import com.viomanager.contest.vo.ContestDetailVO;
import com.viomanager.contest.vo.PoolItemVO;
import java.util.List;

/**
 * 外部比赛待处理池服务（P01–P03）。
 * 仅管理端可见；PENDING 状态可确认（预填创建 contest + 回填 confirmed_contest_id）或忽略。
 */
public interface IContestPoolService {

    /** P01 待处理列表（筛选 status；展示标题、来源、抓取内容、解析线索 clues） */
    Result<List<PoolItemVO>> listPool(String status);

    /** P02 确认为正式比赛（按解析内容预填创建 contest，confirmed_contest_id 回填） */
    Result<ContestDetailVO> confirmPool(Long id, ContestCreateDTO dto);

    /** P03 忽略 */
    Result<Void> ignorePool(Long id);
}
