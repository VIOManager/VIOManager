package com.viomanager.ai.controller;

import com.viomanager.ai.config.RagProperties;
import com.viomanager.ai.dto.RagSyncDTO;
import com.viomanager.ai.service.RagIndexService;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 内部同步端点（拆步 2，KB 服务薄通知专用）——调用方是服务不是人，双闸设计：
 * ① JwtAuthFilter 白名单放行（vio.auth.skip-paths）② X-RAG-Token 共享密钥头
 * （vio.rag.internal-token，两服务同值；生产用环境变量 RAG_INTERNAL_TOKEN 覆盖 dev 默认值）。
 * 不做用户级审计——入库动作发生在 KB 侧且有 KB 操作审计，此处只消费。
 */
@RestController
@RequestMapping("/api/ai/rag")
public class RagIndexController {

    private final RagIndexService indexService;

    private final RagProperties properties;

    public RagIndexController(RagIndexService indexService, RagProperties properties) {
        this.indexService = indexService;
        this.properties = properties;
    }

    /** content 非空 = 重切入库（返回块数）；空 = 下架/删除清索引。令牌缺失/不匹配一律 1002，fail-closed */
    @PostMapping("/sync")
    public Result<Map<String, Object>> sync(@Valid @RequestBody RagSyncDTO dto,
                                            @RequestHeader(value = "X-RAG-Token", required = false) String token) {
        String expected = properties.internalToken();
        if (expected == null || expected.isBlank() || !expected.equals(token)) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "RAG 内部同步令牌不正确");
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            indexService.removeDocument(dto.getDocId());
            return Result.ok(Map.of("removed", true));
        }
        int chunks = indexService.syncDocument(dto.getDocId(), dto.getDocTitle(), dto.getContent());
        return Result.ok(Map.of("chunks", chunks));
    }
}
