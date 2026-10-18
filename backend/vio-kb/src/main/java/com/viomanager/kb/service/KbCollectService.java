package com.viomanager.kb.service;

import com.viomanager.common.result.Result;
import com.viomanager.common.result.PageResult;
import com.viomanager.kb.vo.KbCollectVO;
import java.util.List;

/**
 * 知识库收藏服务（KB05-07）。
 */
public interface KbCollectService {

    /** KB05 收藏：撞 uk_kb_collect 唯一键返回 9001；collect_count 同事务 +1 */
    Result<Void> collect(Long docId);

    /** KB06 取消收藏：幂等（未收藏不报错）；collect_count 同事务 -1 */
    Result<Void> uncollect(Long docId);

    /** KB07 我的收藏列表（时间倒序，带文档摘要；仅显示当前仍 PUBLISHED 的文档） */
    Result<PageResult<KbCollectVO>> myCollections(Integer page, Integer size);
}
