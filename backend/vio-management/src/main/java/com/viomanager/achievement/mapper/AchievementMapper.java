package com.viomanager.achievement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.achievement.entity.Achievement;
import com.viomanager.common.datascop.DataScope;
import org.apache.ibatis.annotations.Mapper;

/**
 * 成果 Mapper。
 *
 * ⚠️ 本表有 org_id，是数据范围过滤的强依据列；声明 SELF 的理由同 ApprovalMapper：
 * 成果三视角（管理端全量 / 本队成员看本队 / 学生只看自己参与的）语义特殊，由 service 显式控制。
 * 若组长要求管理端成果列表自动按部门子树过滤，改成 @DataScope(scope = ORG)。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface AchievementMapper extends BaseMapper<Achievement> {
}
