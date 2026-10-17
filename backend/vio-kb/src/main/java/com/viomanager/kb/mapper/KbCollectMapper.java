package com.viomanager.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.kb.entity.KbCollect;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库收藏 Mapper。
 *
 * @DataScope 声明为 SELF 的理由：本表所有查询都由 service 显式带 member_id = 当前用户
 * （收藏判断 / 我的收藏列表），本人参与语义由业务 SQL 自带。防重复靠
 * uk_kb_collect(doc_id, member_id) 唯一键，DuplicateKeyException 在 service 转 9001。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface KbCollectMapper extends BaseMapper<KbCollect> {
}
