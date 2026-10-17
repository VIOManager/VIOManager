package com.viomanager.kb.service;

import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.kb.dto.KbCommentCreateDTO;
import com.viomanager.kb.vo.KbCommentVO;

/**
 * 知识库评论服务（KB08-11）。
 */
public interface KbCommentService {

    /** KB08 评论列表：顶层分页时间正序 + 楼中楼折叠（父级不可见整楼不返回） */
    Result<PageResult<KbCommentVO>> list(Long docId, Integer page, Integer size);

    /** KB09 发表/回复：父评论须存在且同文档；comment_count 同事务 +1 */
    Result<Long> create(Long docId, KbCommentCreateDTO dto);

    /** KB10 删除：本人或管理员；可见状态的评论删除才 -1（隐藏的未计数，不重复减） */
    Result<Void> delete(Long commentId);

    /** KB11 隐藏/恢复（管理员）：计数随可见口径 ±1 */
    Result<Void> hide(Long commentId, boolean hidden);
}
