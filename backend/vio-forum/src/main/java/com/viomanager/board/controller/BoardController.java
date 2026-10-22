package com.viomanager.board.controller;

import com.viomanager.board.service.IBoardService;
import com.viomanager.board.vo.BoardVO;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版块接口：B01 版块列表（服务端下发，替换前端硬编码 BOARDS——forum/README TODO 落地）。
 * 登录即可调，只读不挂幂等与审计。
 */
@RestController
@RequestMapping("/api/forum/boards")
public class BoardController {

    @Resource
    private IBoardService boardService;

    /**
     * B01 版块列表：NORMAL 版块按 sort_order 升序。
     */
    @GetMapping
    public Result<List<BoardVO>> list() {
        return Result.ok(boardService.listEnabled());
    }
}
