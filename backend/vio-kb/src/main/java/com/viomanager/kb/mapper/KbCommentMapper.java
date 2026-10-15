package com.viomanager.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.kb.entity.KbComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库评论 Mapper。
 *
 * @DataScope 声明为 ALL 的理由：评论可见性与所属文档一致（文档级门槛在 service 判定
 * requirePublished），对能进文档的人是公共数据。不声明的话学生默认被拒（同 KbDocMapper 注释）。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface KbCommentMapper extends BaseMapper<KbComment> {
}
