package com.viomanager.ai.service;

import com.viomanager.ai.support.RagChunker;
import com.viomanager.common.enums.AiErrorCode;
import com.viomanager.common.exception.BizException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * RAG 索引服务（拆步 2）：KB 文档 → 切片 → embedding → pgvector。
 * 文档更新即重切（方案 §五）：先按 metadata.docId 删旧块再插新块，幂等不残留。
 * docId 刻意以字符串存 metadata——pgvector 的 filter 走 metadata 文本比较，
 * 数字类型在 JSON 列与 filter 值之间不一致会静默失配（删块失效=旧块永残留）。
 */
@Service
public class RagIndexService {

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    private final RagChunker chunker;

    public RagIndexService(ObjectProvider<VectorStore> vectorStoreProvider, RagChunker chunker) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.chunker = chunker;
    }

    /**
     * 全量重切：返回入库块数。
     * RAG 未启用抛 9105——调用方是 KB 薄通知（best-effort 只记日志），用户侧消费者到拆步 3/4 才有。
     */
    public int syncDocument(long docId, String docTitle, String content) {
        VectorStore store = requireStore();
        deleteChunks(store, docId);
        List<String> pieces = chunker.chunk(content);
        if (pieces.isEmpty()) {
            return 0;
        }
        List<Document> docs = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            Map<String, Object> metadata = Map.of(
                    "docId", String.valueOf(docId),
                    "docTitle", docTitle == null ? "" : docTitle,
                    "chunkIndex", i,
                    "chunkCount", pieces.size());
            docs.add(new Document(pieces.get(i), metadata));
        }
        store.add(docs);
        return pieces.size();
    }

    /** 清索引：文档下架/删除时调用；无块时是 no-op */
    public void removeDocument(long docId) {
        deleteChunks(requireStore(), docId);
    }

    private void deleteChunks(VectorStore store, long docId) {
        store.delete(new Filter.Expression(Filter.ExpressionType.EQ,
                new Filter.Key("docId"), new Filter.Value(String.valueOf(docId))));
    }

    private VectorStore requireStore() {
        VectorStore store = vectorStoreProvider.getIfAvailable();
        if (store == null) {
            throw new BizException(AiErrorCode.AI_RAG_NOT_ENABLED);
        }
        return store;
    }
}
