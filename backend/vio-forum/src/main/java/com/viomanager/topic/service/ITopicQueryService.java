package com.viomanager.topic.service;

import com.viomanager.common.result.PageResult;
import com.viomanager.topic.vo.ForumStatsVO;
import com.viomanager.topic.vo.HotTopicVO;
import com.viomanager.topic.vo.TopicDetailVO;
import com.viomanager.topic.vo.TopicItemVO;
import java.util.List;

/**
 * 主题查询服务（F01-F04）。
 */
public interface ITopicQueryService {

    /**
     * F01 帖子列表：版块/排序（latest 最新最热 hot）/关键词/分页；
     * 可见状态 = NORMAL + LOCKED（HIDDEN 一律过滤），置顶恒排最前。
     * includeHidden：仅管理档（超管+部门管理员）生效且服务端强制鉴权——管理列表含 HIDDEN 软删帖，
     * 非管理档传 true 一律忽略。
     */
    PageResult<TopicItemVO> list(String board, String sort, String keyword, long page, long size, boolean includeHidden);

    /**
     * F02 今日热议 top5：24h 活跃窗（LIMIT 500 候选兜底）按回复数排序（方案 §3.3）。
     */
    List<HotTopicVO> hotTopics();

    /**
     * F03 社区运行状况。
     */
    ForumStatsVO stats();

    /**
     * F04 帖子详情：浏览数 +1；liked/collected 按当前用户拼装；作者统计聚合。
     */
    TopicDetailVO detail(Long id);
}
