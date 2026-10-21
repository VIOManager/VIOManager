package com.viomanager.board.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.board.entity.ForumBoard;
import com.viomanager.board.mapper.ForumBoardMapper;
import com.viomanager.board.service.IBoardService;
import com.viomanager.board.vo.BoardVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 论坛版块服务实现。版块表数据量个位数，直接查库不做缓存。
 */
@Service
public class BoardServiceImpl implements IBoardService {

    @Resource
    private ForumBoardMapper forumBoardMapper;

    @Override
    public List<BoardVO> listEnabled() {
        List<ForumBoard> boards = forumBoardMapper.selectList(
                new QueryWrapper<ForumBoard>()
                        .eq("status", "NORMAL")
                        .orderByAsc("sort_order"));
        List<BoardVO> vos = new ArrayList<>();
        for (ForumBoard b : boards) {
            BoardVO vo = new BoardVO();
            vo.setCode(b.getCode());
            vo.setName(b.getName());
            vo.setDesc(b.getDescription());
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public ForumBoard findByCode(String code) {
        return forumBoardMapper.selectOne(
                new QueryWrapper<ForumBoard>().eq("code", code).eq("status", "NORMAL"));
    }

    @Override
    public List<ForumBoard> listAll() {
        return forumBoardMapper.selectList(new QueryWrapper<>());
    }
}
