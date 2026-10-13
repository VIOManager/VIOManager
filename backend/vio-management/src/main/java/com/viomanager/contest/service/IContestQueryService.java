package com.viomanager.contest.service;

import com.viomanager.common.result.Result;
import com.viomanager.contest.vo.ContestDetailVO;
import com.viomanager.contest.vo.ContestListItemVO;
import java.util.List;

/**
 * 比赛查询服务（C01 列表 / C02 详情）。
 * 数据范围：超管全部 / 部门管理员本组织子树 / 学生仅 PUBLISHED 及之后状态。
 */
public interface IContestQueryService {

    /** C01 列表：筛选 semesterId/status；含学期名、状态、报名/比赛起止时间、trackCount、teamCount */
    Result<List<ContestListItemVO>> listContests(Long semesterId, String status);

    /** C02 详情：返回合并后生效规则 rules + 每项来源标注 + tracks 列表 */
    Result<ContestDetailVO> getContestDetail(Long id);
}
