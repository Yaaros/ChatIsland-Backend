package io.g8.customai.knowledge.controller;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.g8.customai.common.constants.KnowLedgeEnvs;
import io.g8.customai.common.security.jwt.JwtUtil;
import io.g8.customai.knowledge.entity.KnowledgeBase;
import io.g8.customai.knowledge.service.KnowledgeBaseService;
import io.g8.customai.knowledge.store.RedisEmbeddingStore;
import jakarta.persistence.NoResultException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeBaseController {

    @Autowired
    private RedisEmbeddingStore embeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    private final DocumentSplitter splitter = new DocumentByParagraphSplitter(100, 20);

    // ============= 新增接口 =============

    /**
     * 创建新知识库
     * POST /api/knowledge/new?uid=xxx
     */
    @PostMapping("/new")
    public ResponseEntity<?> createKnowledgeBase(
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "uid", required = false) String uid,
            @RequestBody Map<String, Object> request) {
//        try {
            // 解析UID
            uid = resolveUid(token, uid);

            // 获取请求参数
            String name = (String) request.get("name");
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) request.getOrDefault("tags", new ArrayList<>());

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "知识库名称不能为空"
                ));
            }

            KnowledgeBase kb = request.get("description")==null?
                    knowledgeBaseService.createKnowledgeBase(uid, name.trim(), tags):
                    knowledgeBaseService.createKnowledgeBaseWithDescription(uid, name.trim(), tags,
                                                     request.get("description").toString().trim());


            // 创建知识库

            System.out.println(kb);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "知识库创建成功",
                    "data", Map.of(
                            "kid", kb.getKid(),
                            "name", kb.getName(),
                            "tags", kb.getTags(),
                            "createdTime", kb.getCreatedTime()
                    )
            ));

//        } catch (Exception e) {
//            System.out.println(e);
//            return ResponseEntity.internalServerError().body(Map.of(
//                    "error", "创建知识库失败: " + e.getMessage()
//            ));
//        }
    }

    /**
     * 获取用户所有知识库信息
     * GET /api/knowledge/list?uid=xxx
     */
    @GetMapping("/list")
    public ResponseEntity<?> getUserKnowledgeBases(
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "uid", required = false) String uid) {
        try {
            // 解析UID
            uid = resolveUid(token, uid);

            // 获取知识库列表
            List<Map<String, Object>> knowledgeBases = knowledgeBaseService.getUserKnowledgeBases(uid);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", knowledgeBases,
                    "total", knowledgeBases.size()
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "获取知识库列表失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取特定知识库详细信息
     * GET /api/knowledge/info?uid=xxx&kid=xxx
     */
    @GetMapping("/info")
    public ResponseEntity<?> getKnowledgeBaseInfo(
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "uid", required = false) String uid,
            @RequestParam("kid") String kid) {
        try {
            // 解析UID
            uid = resolveUid(token, uid);

            // 获取知识库信息
            Map<String, Object> kbInfo = knowledgeBaseService.getKnowledgeBaseInfo(uid, kid);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", kbInfo
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "获取知识库信息失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 删除特定知识库
     * DELETE /api/knowledge/delete-kb?uid=xxx&kid=xxx
     */
    @DeleteMapping("/delete-kb")
    public ResponseEntity<?> deleteKnowledgeBase(
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "uid", required = false) String uid,
            @RequestParam("kid") String kid) {
        try {
            // 解析UID和权限检查
            String requestUid = resolveUid(token, uid);

            // 权限检查：只有管理员可以删除其他用户的知识库
            if (uid != null && !Objects.equals(requestUid, uid)) {
                String role = jwtUtil.getRoleFromToken(token.substring(7));
                if (!"ADMIN".equals(role)) {
                    return ResponseEntity.status(403).body(Map.of(
                            "error", "非管理员用户只能删除自己的知识库"
                    ));
                }
                requestUid = uid; // 管理员操作时使用目标用户ID
            }

            // 删除知识库
            System.out.println(knowledgeBaseService.testRedisConnection());

            knowledgeBaseService.deleteKnowledgeBase(requestUid, kid);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "知识库删除成功"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "删除知识库失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 删除知识库中的特定文档
     * DELETE /api/knowledge/document?docId=xxx
     */
    @DeleteMapping("/delete-doc")
    public ResponseEntity<?> deleteDocument(
            @RequestHeader("Authorization") String token,
            @RequestParam("docId") String docId) {
        try {
            // 解析UID
            String uid = resolveUid(token, null);

            // 删除文档
            knowledgeBaseService.deleteDocument(uid, docId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "文档删除成功"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "删除文档失败: " + e.getMessage()
            ));
        }
    }

    // ============= 修改后的原有接口 =============

    /**
     * 文档上传接口（修改版）
     */
    @PostMapping(value="/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "uid", required = false) String uid,
            @RequestParam("kid") String kid,
            @RequestPart("file") MultipartFile file) {
        try {
            // 解析UID
            uid = resolveUid(token, uid);

            // 检查知识库是否存在
            if (!knowledgeBaseService.knowledgeBaseExists(uid, kid)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "知识库不存在，请先创建知识库"
                ));
            }

            // 文档处理流水线
            Document document = parseDocument(file);
            List<TextSegment> segments = splitter.split(document);
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            // 生成文档ID
            String docId = UUID.randomUUID().toString();

            // 存储到用户专属空间
            embeddingStore.addAllForUser(uid, kid, embeddings, segments);

            // 记录文档信息到数据库
            String fileType = getFileExtension(file.getOriginalFilename());
            knowledgeBaseService.addDocument(uid, kid, docId,
                    file.getOriginalFilename(),
                    fileType,
                    file.getSize(),
                    segments.size());

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "文档上传成功",
                    "data", Map.of(
                            "docId", docId,
                            "filename", file.getOriginalFilename(),
                            "segments", segments.size(),
                            "fileSize", file.getSize()
                    )
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "文档处理失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 知识检索接口（保持原有逻辑）
     */
    @PostMapping("/search")
    public ResponseEntity<?> searchKnowledge(
            @RequestBody Map<String,String> input,
            @RequestHeader("Authorization") String token) {
        try {
            String uid = resolveUid(token, input.get("uid"));
            String kid = input.get("kid");

            // 检查知识库是否存在
            if (!knowledgeBaseService.knowledgeBaseExists(uid, kid)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "知识库不存在"
                ));
            }

            // 生成问题向量
            Embedding queryEmbedding = embeddingModel.embed(input.get("question")).content();

            // 构建搜索请求
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(Integer.valueOf(input.getOrDefault("maxResults", "3")))
                    .minScore(Double.valueOf(input.getOrDefault("minScore", "0.5")))
                    .build();

            // 执行用户专属搜索
            EmbeddingSearchResult<TextSegment> result = embeddingStore.searchForUser(uid, kid, searchRequest);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", result.matches().stream()
                            .map(m -> Map.of(
                                    "text", m.embedded().text(),
                                    "score", m.score()
                            ))
                            .collect(Collectors.toList())
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "搜索失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 删除接口（修改版 - 删除所有知识库）
     */
    @DeleteMapping("/delete-all")
    public ResponseEntity<?> deleteAllKnowledge(
            @RequestParam(value = "uid", required = false) String targetUid,
            @RequestHeader("Authorization") String token) {
        try {
            String operatorUid = resolveUid(token, null);

            // 权限检查
            if (targetUid != null && !Objects.equals(operatorUid, targetUid)) {
                String role = jwtUtil.getRoleFromToken(token.substring(7));
                if (!"ADMIN".equals(role)) {
                    return ResponseEntity.status(403).body(Map.of(
                            "error", "非管理员用户只能删除自己的知识库"
                    ));
                }
                operatorUid = targetUid; // 管理员操作时使用目标用户ID
            }

            // 删除用户所有知识库
            knowledgeBaseService.deleteAllKnowledgeBases(operatorUid);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "所有知识库删除成功"
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "删除失败: " + e.getMessage()
            ));
        }
    }

    // ============= 辅助方法 =============

    private Document parseDocument(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if(filename==null)throw new NoResultException("文件名不能为空");
        String[] arr = filename.toLowerCase().split("\\.");
        if(arr.length<2)throw new UnsupportedOperationException("不支持的文档格式: " + filename);
        filename = arr[arr.length-1];
        if (KnowLedgeEnvs.SUPPORTED_FILES.contains(filename)) {
            return new ApacheTikaDocumentParser().parse(file.getInputStream());
        }
        throw new UnsupportedOperationException("不支持的文档格式: " + filename);
    }

    private String resolveUid(String token, String requestUid) {
        if (requestUid != null && !requestUid.trim().isEmpty()) {
            return requestUid.trim();
        }
        if (!token.startsWith("Bearer ")) {
            throw new IllegalArgumentException("无效的JWT传递方式");
        }
        return jwtUtil.getUidFromToken(token.substring(7));
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}