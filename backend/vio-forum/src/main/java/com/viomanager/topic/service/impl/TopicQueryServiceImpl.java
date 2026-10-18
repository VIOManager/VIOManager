package com.viomanager.topic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.board.entity.ForumBoard;
import com.viomanager.board.service.IBoardService;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.ForumErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.reply.mapper.ForumReplyMapper;
import com.viomanager.topic.entity.ForumTopic;
import com.viomanager.topic.entity.ForumTopicCollect;
import com.viomanager.topic.entity.ForumTopicLike;
import com.viomanager.topic.mapper.ForumTopicCollectMapper;
import com.viomanager.topic.mapper.ForumTopicLikeMapper;
import com.viomanager.topic.mapper.ForumTopicMapper;
import com.viomanager.topic.service.ITopicQueryService;
import com.viomanager.topic.vo.AuthorStatsVO;
import com.viomanager.topic.vo.ForumStatsVO;
import com.viomanager.topic.vo.HotTopicVO;
import com.viomanager.topic.vo.TopicDetailVO;
import com.viomanager.topic.vo.TopicItemVO;
import com.viomanager.support.TagCodec;
import com.viomanager.user.service.IForumUserService;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 主题查询服务实现（F01-F04）。可见口径：NORMAL + LOCKED 可见，HIDDEN 对所有人不可见。
 */
@Service
public class TopicQueryServiceImpl implements ITopicQueryService {

    /** F02 热议候选集上限（方案 §3.3：防灌入大量历史帖导致内存排序失控） */
    private static final int HOT_CANDIDATE_LIMIT = 500;
    private static final int HOT_TOP_N = 5;
    /** 列表分页 size 上限（规范 §2.3：size 上限由业务层校验） */
    private static final int MAX_PAGE_SIZE = 50;

    @Resource
    private ForumTopicMapper forumTopicMapper;
    @Resource
    private ForumTopicLikeMapper forumTopicLikeMapper;
    @Resource
    private ForumTopicCollectMapper forumTopicCollectMapper;
    @Resource
    private ForumReplyMapper forumReplyMapper;
    @Resource
    private IBoardService boardService;
    @Resource
    private IForumUserService forumUserService;

    @Override
    public PageResult<TopicItemVO> list(String board, String sort, String keyword, long page, long size, boolean includeHidden) {
        //1.分页参数收口：page 至少 1，size 默认 20 上限 50
        long p = Math.max(page, 1);
        long s = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        //2.组装条件：可见状态 + 可选版块 + 可选标题模糊。
        //  includeHidden 仅管理档（两档）生效且服务端强制：管理列表要看到 HIDDEN 软删帖做恢复；
        //  学生传 true 一律忽略——HIDDEN 对非管理档不可见是硬口径，不靠前端自觉
        QueryWrapper<ForumTopic> wrapper = new QueryWrapper<>();
        if (includeHidden && isForumAdmin(UserHolder.getUser())) {
            wrapper.in("status", "NORMAL", "LOCKED", "HIDDEN");
        } else {
            wrapper.in("status", "NORMAL", "LOCKED");
        }
        if (board != null && !board.isEmpty()) {
            ForumBoard fb = boardService.findByCode(board);
            //未知版块编码：不过发帖守卫（那是 9104 的事），列表按空结果处理最友好
            if (fb == null) {
                return PageResult.of(new ArrayList<>(), 0, p, s);
            }
            wrapper.eq("board_id", fb.getId());
        }
        if (keyword != null && !keyword.isEmpty()) {
            //接口清单口径：关键词模糊匹配"标题与标签"（搜索框占位文案同此）
            wrapper.and(w -> w.like("title", keyword).or().like("tags", keyword));
        }
        //3.排序：置顶恒最前，再按 latest（最后活跃）/hot（回复数）
        wrapper.orderByDesc("pinned");
        if ("hot".equals(sort)) {
            wrapper.orderByDesc("reply_count");
        } else {
            wrapper.orderByDesc("last_reply_at");
        }
        Page<ForumTopic> result = forumTopicMapper.selectPage(new Page<>(p, s), wrapper);
        //4.拼装 VO：board_id→code 映射 + 热议标记（与 F02 同一计算）
        Map<Long, String> codeMap = boardCodeMap();
        Set<Long> hotIds = hotTopicIds();
        List<TopicItemVO> vos = new ArrayList<>();
        for (ForumTopic t : result.getRecords()) {
            TopicItemVO vo = new TopicItemVO();
            vo.setId(t.getId());
            vo.setBoard(codeMap.get(t.getBoardId()));
            vo.setTitle(t.getTitle());
            vo.setAuthor(t.getAuthorName());
            vo.setAuthorGroup(t.getAuthorGroup());
            vo.setTags(TagCodec.split(t.getTags()));
            vo.setReplyCount(t.getReplyCount());
            vo.setViewCount(t.getViewCount());
            vo.setLikeCount(t.getLikeCount());
            vo.setLastActiveAt(t.getLastReplyAt());
            vo.setLastReplyBy(t.getLastReplyByName());
            vo.setPinned(t.getPinned() != null && t.getPinned() == 1);
            // 状态透出（归档③）：前端 locked 守卫与管理端列表标记依赖此字段
            vo.setStatus(t.getStatus());
            vo.setHot(hotIds.contains(t.getId()));
            vos.add(vo);
        }
        return PageResult.of(vos, result.getTotal(), p, s);
    }

    @Override
    public List<HotTopicVO> hotTopics() {
        List<HotTopicVO> vos = new ArrayList<>();
        for (ForumTopic t : hotCandidates()) {
            HotTopicVO vo = new HotTopicVO();
            vo.setId(t.getId());
            vo.setTitle(t.getTitle());
            vo.setAuthor(t.getAuthorName());
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public ForumStatsVO stats() {
        ForumStatsVO vo = new ForumStatsVO();
        //收窄安全：社区量级计数远不到 int 上限（L10 口径：VO 计数字段出 int 真数字）
        vo.setMemberCount((int) forumUserService.countMembers());
        vo.setTopicCount(forumTopicMapper.selectCount(
                new QueryWrapper<ForumTopic>().in("status", "NORMAL", "LOCKED")).intValue());
        vo.setReplyCount(forumReplyMapper.selectCount(new QueryWrapper<>()).intValue());
        return vo;
    }

    @Override
    public TopicDetailVO detail(Long id) {
        //1.可见性：不存在/已删/隐藏一律 9102
        ForumTopic t = forumTopicMapper.selectById(id);
        if (t == null || "HIDDEN".equals(t.getStatus())) {
            throw new BizException(ForumErrorCode.TOPIC_NOT_FOUND);
        }
        //2.浏览数 +1（原子自增，不读改写）
        forumTopicMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ForumTopic>()
                        .eq("id", id)
                        .setSql("view_count = view_count + 1"));
        //3.当前用户态
        Long userId = UserHolder.getUser().getUserId();
        boolean liked = forumTopicLikeMapper.selectCount(
                new QueryWrapper<ForumTopicLike>().eq("topic_id", id).eq("user_id", userId)) > 0;
        boolean collected = forumTopicCollectMapper.selectCount(
                new QueryWrapper<ForumTopicCollect>().eq("topic_id", id).eq("user_id", userId)) > 0;
        //4.拼装
        TopicDetailVO vo = new TopicDetailVO();
        vo.setId(t.getId());
        vo.setBoard(boardCodeMap().get(t.getBoardId()));
        vo.setTitle(t.getTitle());
        vo.setAuthor(t.getAuthorName());
        vo.setAuthorGroup(t.getAuthorGroup());
        vo.setTags(TagCodec.split(t.getTags()));
        vo.setReplyCount(t.getReplyCount());
        vo.setViewCount(t.getViewCount() + 1);
        vo.setLikeCount(t.getLikeCount());
        vo.setLiked(liked);
        vo.setCollected(collected);
        vo.setPinned(t.getPinned() != null && t.getPinned() == 1);
        // 状态透出（归档③）：前端 locked 守卫依赖此字段（F12 锁帖后回复框置灰）
        vo.setStatus(t.getStatus());
        vo.setPublishAt(t.getCreatedAt());
        vo.setBody(splitBody(t.getBody()));
        vo.setAttach(new ArrayList<>());
        vo.setAuthorStats(authorStats(t.getAuthorUserId()));
        return vo;
    }

    /** 论坛权限两档（2026-10-17 拍板）：超管或部门管理员——includeHidden 管理列表的准入判定 */
    private boolean isForumAdmin(CurrentUser user) {
        return user != null && ("SUPER_ADMIN".equals(user.getRole()) || "DEPT_ADMIN".equals(user.getRole()));
    }

    /**
     * 热议候选集（F02 与列表 hot 标记共用）：24h 活跃窗取最近 500 条，再按回复数排前 5。
     */
    private List<ForumTopic> hotCandidates() {
        List<ForumTopic> candidates = forumTopicMapper.selectList(
                new QueryWrapper<ForumTopic>()
                        .eq("status", "NORMAL")
                        .ge("last_reply_at", LocalDateTime.now().minusHours(24))
                        .orderByDesc("last_reply_at")
                        .last("limit " + HOT_CANDIDATE_LIMIT));
        //窗口内行数量级小，内存按回复数排序取 top5（方案 §3.3 两步取数）
        candidates.sort((a, b) -> b.getReplyCount() - a.getReplyCount());
        return candidates.subList(0, Math.min(candidates.size(), HOT_TOP_N));
    }

    private Set<Long> hotTopicIds() {
        Set<Long> ids = new HashSet<>();
        for (ForumTopic t : hotCandidates()) {
            ids.add(t.getId());
        }
        return ids;
    }

    /**
     * 作者统计（方案 §3.5）：主题数 / 主题获赞求和（楼层赞不计）/ 被收藏求和。
     */
    private AuthorStatsVO authorStats(Long authorUserId) {
        AuthorStatsVO vo = new AuthorStatsVO();
        //收窄安全：作者维度计数远不到 int 上限（L10 口径：VO 计数字段出 int 真数字）
        vo.setPostCount(forumTopicMapper.selectCount(
                new QueryWrapper<ForumTopic>().eq("author_user_id", authorUserId)).intValue());
        Object likeSum = forumTopicMapper.selectObjs(
                new QueryWrapper<ForumTopic>()
                        .select("IFNULL(SUM(like_count), 0)")
                        .eq("author_user_id", authorUserId)).get(0);
        Object collectSum = forumTopicMapper.selectObjs(
                new QueryWrapper<ForumTopic>()
                        .select("IFNULL(SUM(collect_count), 0)")
                        .eq("author_user_id", authorUserId)).get(0);
        vo.setLikeTotal(likeSum == null ? 0 : Integer.parseInt(likeSum.toString()));
        vo.setCollectedCount(collectSum == null ? 0 : Integer.parseInt(collectSum.toString()));
        return vo;
    }

    /** board_id → code 映射（版块表个位数行，每次查库即可） */
    private Map<Long, String> boardCodeMap() {
        Map<Long, String> map = new HashMap<>();
        for (ForumBoard b : boardService.listAll()) {
            map.put(b.getId(), b.getCode());
        }
        return map;
    }

    /**
     * 正文 \n 分段（方案 §3.5：存储 \n 分段，出参按段渲染）。
     * 空行不产出空段：表单里多敲回车/管理端编辑回填都可能出现连续换行，空段在详情页渲染成空块
     */
    private List<String> splitBody(String body) {
        List<String> list = new ArrayList<>();
        if (body == null || body.isEmpty()) {
            return list;
        }
        for (String p : body.split("\n")) {
            if (!p.isBlank()) {
                list.add(p);
            }
        }
        return list;
    }
}
