package com.viomanager.topic.controller;

import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.topic.service.ITopicQueryService;
import com.viomanager.topic.vo.ForumStatsVO;
import com.viomanager.topic.vo.HotTopicVO;
import com.viomanager.topic.vo.TopicDetailVO;
import com.viomanager.topic.vo.TopicItemVO;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主题查询接口（F01-F04）。登录即可调，只读不挂幂等与审计。
 * Spring MVC 精确路径（/hot、/stats）优先于路径变量（/{id}），无占位吞路径问题。
 */
@RestController
@RequestMapping("/api/forum/topics")
public class TopicQueryController {

    @Resource
    private ITopicQueryService topicQueryService;

    /**
     * F01 帖子列表：board/sort(latest|hot)/keyword/page/size/includeHidden。
     * includeHidden 仅管理档（两档）生效，服务端强制鉴权（学生传了也忽略）。
     */
    @GetMapping
    public Result<PageResult<TopicItemVO>> list(
            @RequestParam(required = false) String board,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") long page,
            @RequestParam(required = false, defaultValue = "20") long size,
            @RequestParam(required = false, defaultValue = "false") boolean includeHidden) {
        return Result.ok(topicQueryService.list(board, sort, keyword, page, size, includeHidden));
    }

    /**
     * F02 今日热议 top5。
     */
    @GetMapping("/hot")
    public Result<List<HotTopicVO>> hot() {
        return Result.ok(topicQueryService.hotTopics());
    }

    /**
     * F03 社区运行状况。
     */
    @GetMapping("/stats")
    public Result<ForumStatsVO> stats() {
        return Result.ok(topicQueryService.stats());
    }

    /**
     * F04 帖子详情（服务端浏览计数 +1）。
     */
    @GetMapping("/{id}")
    public Result<TopicDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(topicQueryService.detail(id));
    }
}
