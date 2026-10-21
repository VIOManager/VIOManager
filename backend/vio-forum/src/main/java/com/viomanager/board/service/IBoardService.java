package com.viomanager.board.service;

import com.viomanager.board.entity.ForumBoard;
import com.viomanager.board.vo.BoardVO;
import java.util.List;

/**
 * 论坛版块服务。
 */
public interface IBoardService {

    /**
     * B01 版块列表：NORMAL 状态按 sort_order 升序，登录即可调。
     */
    List<BoardVO> listEnabled();

    /**
     * 按 code 查启用版块，给发帖守卫用；不存在或已停用返回 null（调用方按 9104 处理）。
     */
    ForumBoard findByCode(String code);

    /**
     * 全部未删版块（含 DISABLED）：给 board_id→code 映射用（列表/详情出参拼装）。
     */
    List<ForumBoard> listAll();
}
