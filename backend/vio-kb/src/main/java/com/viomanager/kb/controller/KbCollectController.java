package com.viomanager.kb.controller;

import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.kb.service.KbCollectService;
import com.viomanager.kb.vo.KbCollectVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库收藏接口（KB05-07），登录即可。
 * 收藏走收藏/取消两个独立端点（权威清单口径；前端暂定版是 toggle，联调时调整）。
 */
@RestController
@RequestMapping("/api/kb")
public class KbCollectController {

    @Resource
    private KbCollectService kbCollectService;

    /** KB05 收藏（幂等键；重复收藏 9001） */
    @Idempotent(bizType = "KB_COLLECT")
    @PostMapping("/docs/{id}/collect")
    public Result<Void> collect(@PathVariable("id") Long id) {
        return kbCollectService.collect(id);
    }

    /** KB06 取消收藏（幂等：未收藏也返回成功） */
    @DeleteMapping("/docs/{id}/collect")
    public Result<Void> uncollect(@PathVariable("id") Long id) {
        return kbCollectService.uncollect(id);
    }

    /** KB07 我的收藏列表（时间倒序，带文档摘要） */
    @GetMapping("/my/collections")
    public Result<PageResult<KbCollectVO>> myCollections(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return kbCollectService.myCollections(page, size);
    }
}
