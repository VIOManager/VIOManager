package com.viomanager.kb.service;

import com.viomanager.common.result.Result;
import com.viomanager.kb.dto.KbAdminQueryDTO;
import com.viomanager.kb.dto.KbDocCreateDTO;
import com.viomanager.kb.dto.KbDocQueryDTO;
import com.viomanager.kb.dto.KbDocUpdateDTO;
import com.viomanager.kb.vo.KbAdminDocVO;
import com.viomanager.kb.vo.KbDocDetailVO;
import com.viomanager.kb.vo.KbDocVO;
import com.viomanager.kb.vo.KbPromptVO;
import com.viomanager.common.result.PageResult;
import java.util.List;

/**
 * 知识库文档服务：读口（KB01-04/KB17）委托 KbDocQuerySupport，写口（KB12-16）在本实现。
 */
public interface KbDocService {

    /** KB01 浏览列表：仅 PUBLISHED，置顶优先再时间倒序，keyword 走 FULLTEXT */
    Result<List<KbDocVO>> list(KbDocQueryDTO dto);

    /** KB02 详情：正文 + 附件信息 + 是否已收藏；view_count 原子 +1 */
    Result<KbDocDetailVO> detail(Long id);

    /** KB03 推荐位：置顶 + 最新 N（默认 5 上限 10），本地缓存 60s */
    Result<List<KbDocVO>> recommend(Integer limit);

    /** KB04 AI 提示词列表（is_prompt=1 且 PUBLISHED） */
    Result<List<KbPromptVO>> prompts();

    /** KB17 管理视角列表（含 DRAFT/OFFLINE；部门管理员限本部门，v1 精确匹配） */
    Result<PageResult<KbAdminDocVO>> adminList(KbAdminQueryDTO dto);

    /** KB12 发布/存草稿：LINK 必填 sourceUrl，isPrompt=1 必填 promptSchema */
    Result<KbDocVO> create(KbDocCreateDTO dto);

    /** KB13 编辑全字段；带 updatedAt 时做乐观锁，0 行命中 1007 */
    Result<Void> update(Long id, KbDocUpdateDTO dto);

    /** KB14 逻辑删除（评论收藏不级联清，随文档整体不可见） */
    Result<Void> delete(Long id);

    /** KB15 置顶/取消置顶（原子翻转） */
    Result<Void> toggleTop(Long id);

    /** KB16 上架/下架/发布草稿：DRAFT→PUBLISHED、PUBLISHED↔OFFLINE，条件更新防跳档 */
    Result<Void> updateStatus(Long id, String status);
}
