package com.viomanager.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.approval.entity.NotifyOutbox;
import com.viomanager.common.datascop.DataScope;
import org.apache.ibatis.annotations.Mapper;

/**
 * 本地消息表 Mapper。
 * ⚠️ SELF 声明的理由同 ApprovalMapper：本表由 relay 定时线程读写（无登录态，
 * 拦截器对无登录态本就放行），且事件账本没有"按人可见"的语义，不吃通用注入。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface NotifyOutboxMapper extends BaseMapper<NotifyOutbox> {
}
