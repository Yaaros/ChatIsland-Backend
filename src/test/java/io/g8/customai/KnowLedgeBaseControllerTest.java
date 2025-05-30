package io.g8.customai;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.g8.customai.common.security.jwt.JwtUtil;
import io.g8.customai.knowledge.store.RedisEmbeddingStore;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge")
public class KnowLedgeBaseControllerTest {
    @Autowired
    private RedisEmbeddingStore embeddingStore;
    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private JwtUtil jwtUtil;
    private final DocumentSplitter splitter = new DocumentByParagraphSplitter(100, 10);

    // 文档上传接口
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kid") String kid,
            @RequestHeader("Authorization") String token,
            @RequestBody(required = false) Map<String, String> body) {

        try {
            String uid = resolveUid(token, body.get("uid"));

            // 文档处理流水线
            Document document = parseDocument(file);
            List<TextSegment> segments = splitter.split(document);
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            // 存储到用户知识库
            embeddingStore.addAllForUser(uid, kid, embeddings, segments);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "segments", segments.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "文档处理失败: " + e.getMessage()
            ));
        }
    }


    // 知识检索接口
    @PostMapping("/search")
    public ResponseEntity<?> searchKnowledge(
            @RequestBody SearchRequest request,
            @RequestHeader("Authorization") String token) {

        try {
            String uid = resolveUid(token, request.getUid());
            String kid = request.getKid();

            // 构造搜索请求
            Embedding queryEmbedding = embeddingModel.embed(request.getQuestion()).content();

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(request.getMaxResults())
                    .minScore(Double.valueOf(request.getMinScore()))
                    .build();

            EmbeddingSearchResult<TextSegment> result = embeddingStore.searchForUser(uid, kid, searchRequest);

            return ResponseEntity.ok(result.matches().stream()
                    .map(m -> Map.of(
                            "text", m.embedded().text(),
                            "score", m.score()
                    ))
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "搜索失败: " + e.getMessage()
            ));
        }
    }


    // 删除接口
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteKnowledge(
            @PathVariable String id,
            @RequestParam("kid") String kid,
            @RequestHeader("Authorization") String token,
            @RequestBody(required = false) Map<String, String> body) {

        String uid = resolveUid(token, body.get("uid"));
        embeddingStore.removeForUser(uid, kid, id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }


    private Document parseDocument(MultipartFile file) throws IOException {
        // 根据文件类型选择解析器
        if (Objects.requireNonNull(file.getOriginalFilename()).endsWith(".txt")||
            Objects.requireNonNull(file.getOriginalFilename()).endsWith(".md") ||
            Objects.requireNonNull(file.getOriginalFilename()).endsWith(".pdf")){
            return new TextDocumentParser().parse(file.getInputStream());
        }
        throw new UnsupportedOperationException("不支持的文档格式");
    }

    // 请求参数封装
    @Data
    @Getter
    @Setter
    static class SearchRequest {
        private String question;
        private Integer maxResults = 3;
        private Float minScore = 0.5f;
        private String uid; // 可选覆盖字段
        private String kid;
    }

    // 统一获取UID的方法
    private String resolveUid(String token, String requestUid) {
        if (requestUid != null && !requestUid.isEmpty()) {
            // 这里需要添加权限校验逻辑，例如管理员才能指定UID
            return requestUid;
        }
        return jwtUtil.getUidFromToken(token);
    }
}