package com.viomanager.board.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.board.entity.ForumBoard;
import org.apache.ibatis.annotations.Mapper;

/**
 * 论坛版块 mapper。
 */
@Mapper
public interface ForumBoardMapper extends BaseMapper<ForumBoard> {
}
