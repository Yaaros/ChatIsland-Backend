package io.g8.customai.admin.controller;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.g8.customai.admin.util.Utils;
import io.g8.customai.common.constants.KnowLedgeEnvs;
import io.g8.customai.knowledge.entity.KnowledgeBase;
import io.g8.customai.knowledge.entity.KnowledgeDocument;
import io.g8.customai.knowledge.service.RagService;
import io.g8.customai.common.security.jwt.JwtUtil;
import jakarta.persistence.NoResultException;
import jakarta.validation.constraints.NotNull;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

@RestController
@RequestMapping("/admin/knowledge")
public class KnowledgeBaseAdminController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseAdminController.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RagService ragService;

    @Autowired
    private EmbeddingModel embeddingModel;

    private final DocumentSplitter splitter = new DocumentByParagraphSplitter(100, 20);

    // ============= GET方法 =============

    /**
     * 获取指定用户的指定知识库中的所有文档信息
     * GET /api/admin/knowledge/kb-docs
     */
    @GetMapping("/kb-docs")
    public ResponseEntity<?> getKnowledgeBaseDocuments(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("uid") String uid,
            @RequestParam("kid") String kid) {

        // 管理员权限验证
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            Map<String, Object> kbInfo = ragService.getKnowledgeBaseInfo(uid, kid);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", Map.of(
                            "uid", uid,
                            "kid", kid,
                            "knowledgeBaseName", kbInfo.get("name"),
                            "documents", kbInfo.get("documents")
                    )
            ));

        } catch (Exception e) {
            log.error("管理员获取知识库文档失败, uid: {}, kid: {}, error: {}", uid, kid, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "获取知识库文档失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取指定用户的所有知识库及其文档信息
     * GET /api/admin/knowledge/user-kbs
     */
    @GetMapping("/user-kbs")
    public ResponseEntity<?> getUserKnowledgeBases(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("uid") String uid) {

        // 管理员权限验证
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            List<Map<String, Object>> knowledgeBases = ragService.getUserKnowledgeBases(uid);

            Map<String, List<Map<String, Object>>> result = new HashMap<>();
            for (Map<String, Object> kb : knowledgeBases) {
                String kid = (String) kb.get("kid");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> documents = (List<Map<String, Object>>) kb.get("documents");
                result.put(kid, documents);
            }

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", Map.of(
                            "uid", uid,
                            "knowledgeBases", result
                    )
            ));

        } catch (Exception e) {
            log.error("管理员获取用户知识库失败, uid: {}, error: {}", uid, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "获取用户知识库失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量获取多个用户的知识库信息
     * GET /api/admin/knowledge/batch-users
     */
    @GetMapping("/batch-users")
    public ResponseEntity<?> getBatchUsersKnowledgeBases(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("ids") List<String> userIds) {

        // 管理员权限验证
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            Map<String, Map<String, List<Map<String, Object>>>> result = new HashMap<>();

            for (String uid : userIds) {
                try {
                    List<Map<String, Object>> knowledgeBases = ragService.getUserKnowledgeBases(uid);

                    Map<String, List<Map<String, Object>>> userKbs = new HashMap<>();
                    for (Map<String, Object> kb : knowledgeBases) {
                        String kid = (String) kb.get("kid");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> documents = (List<Map<String, Object>>) kb.get("documents");
                        userKbs.put(kid, documents);
                    }

                    result.put(uid, userKbs);
                } catch (Exception e) {
                    log.warn("获取用户 {} 的知识库信息失败: {}", uid, e.getMessage());
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "批量获取用户知识库失败: " + e.getMessage()
                    ));
                }
            }
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", result
            ));

        } catch (Exception e) {
            log.error("管理员批量获取用户知识库失败, error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "批量获取用户知识库失败: " + e.getMessage()
            ));
        }
    }

    // ============= DELETE方法 =============

    /**
     * 删除用户的所有知识库
     * DELETE /api/admin/knowledge/delete-all-user-kbs
     */
    @DeleteMapping("/delete-all-user-kbs")
    public ResponseEntity<?> deleteAllUserKnowledgeBases(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("uid") String uid) {

        // 管理员权限验证
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            ragService.deleteAllKnowledgeBases(uid);
            log.info("管理员删除用户所有知识库成功, uid: {}", uid);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "用户所有知识库删除成功",
                    "uid", uid
            ));

        } catch (Exception e) {
            log.error("管理员删除用户所有知识库失败, uid: {}, error: {}", uid, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "删除用户所有知识库失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 删除用户的特定知识库
     * DELETE /api/admin/knowledge/delete-user-kb
     */
    @DeleteMapping("/delete-user-kb")
    public ResponseEntity<?> deleteUserKnowledgeBase(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("uid") String uid,
            @RequestParam("kid") String kid) {

        // 管理员权限验证
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            ragService.deleteKnowledgeBase(uid, kid);
            log.info("管理员删除用户知识库成功, uid: {}, kid: {}", uid, kid);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "知识库删除成功",
                    "uid", uid,
                    "kid", kid
            ));

        } catch (Exception e) {
            log.error("管理员删除用户知识库失败, uid: {}, kid: {}, error: {}", uid, kid, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "删除知识库失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 清空用户特定知识库中的所有文档（知识库本身不删除）
     * DELETE /api/admin/knowledge/clear-kb-docs
     */
    @DeleteMapping("/clear-kb-docs")
    public ResponseEntity<?> clearKnowledgeBaseDocuments(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("uid") String uid,
            @RequestParam("kid") String kid) {

        // 管理员权限验证
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            // 获取知识库信息确保存在
            Map<String, Object> kbInfo = ragService.getKnowledgeBaseInfo(uid, kid);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> documents = (List<Map<String, Object>>) kbInfo.get("documents");

            int deletedCount = 0;
            for (Map<String, Object> doc : documents) {
                try {
                    String docId = (String) doc.get("docId");
                    ragService.deleteDocument(uid, docId);
                    deletedCount++;
                } catch (Exception e) {
                    log.warn("删除文档失败, docId: {}, error: {}", doc.get("docId"), e.getMessage());
                }
            }

            log.info("管理员清空知识库文档成功, uid: {}, kid: {}, 删除文档数: {}", uid, kid, deletedCount);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "知识库文档清空成功",
                    "uid", uid,
                    "kid", kid,
                    "deletedDocuments", deletedCount
            ));

        } catch (Exception e) {
            log.error("管理员清空知识库文档失败, uid: {}, kid: {}, error: {}", uid, kid, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "清空知识库文档失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 删除用户特定知识库中的特定文档
     * DELETE /api/admin/knowledge/delete-user-doc
     */
    @DeleteMapping("/delete-user-doc")
    public ResponseEntity<?> deleteUserDocument(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("uid") String uid,
            @RequestParam("docId") String docId) {

        // 管理员权限验证
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            ragService.deleteDocument(uid, docId);
            log.info("管理员删除用户文档成功, uid: {}, docId: {}", uid, docId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "文档删除成功",
                    "uid", uid,
                    "docId", docId
            ));

        } catch (Exception e) {
            log.error("管理员删除用户文档失败, uid: {}, docId: {}, error: {}", uid, docId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "删除文档失败: " + e.getMessage()
            ));
        }
    }

    // ============= POST方法 =============

    /**
     * 为用户创建新知识库
     * POST /api/admin/knowledge/create-user-kb
     */
    @PostMapping("/create-user-kb")
    public ResponseEntity<?> createUserKnowledgeBase(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> request) {

        // 管理员权限验证
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            String uid = (String) request.get("uid");
            String name = (String) request.get("name");
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) request.getOrDefault("tags", new ArrayList<>());
            String description = (String) request.get("description");

            if (uid == null || uid.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "用户ID不能为空"
                ));
            }

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "知识库名称不能为空"
                ));
            }

            KnowledgeBase kb = description == null ?
                    ragService.createKnowledgeBase(uid, name.trim(), tags) :
                    ragService.createKnowledgeBaseWithDescription(uid, name.trim(), tags, description.trim());

            log.info("管理员为用户创建知识库成功, uid: {}, kid: {}, name: {}", uid, kb.getKid(), name);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "知识库创建成功",
                    "data", Map.of(
                            "uid", uid,
                            "kid", kb.getKid(),
                            "name", kb.getName(),
                            "tags", kb.getTags(),
                            "description", kb.getDescription() != null ? kb.getDescription() : "",
                            "createdTime", kb.getCreatedTime()
                    )
            ));

        } catch (Exception e) {
            log.error("管理员创建用户知识库失败, error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "创建知识库失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 为用户的特定知识库上传文档
     * POST /api/admin/knowledge/upload-user-doc
     */
    @PostMapping(value = "/upload-user-doc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadUserDocument(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("uid") String uid,
            @RequestParam("kid") String kid,
            @RequestPart("file") MultipartFile file) {

        // 管理员权限验证
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            // 检查知识库是否存在
            if (!ragService.knowledgeBaseExists(uid, kid)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "知识库不存在"
                ));
            }

            // 文档处理流水线
            Document document = parseDocument(file);
            List<TextSegment> segments = preprocessDocument(document);
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            // 生成文档ID
            String docId = UUID.randomUUID().toString();

            // 存储到Chroma并记录到数据库
            String fileType = getFileExtension(file.getOriginalFilename());
            KnowledgeDocument savedDoc = ragService.addDocument(
                    uid, kid, docId,
                    file.getOriginalFilename(),
                    fileType,
                    file.getSize(),
                    embeddings,
                    segments
            );

            log.info("管理员为用户上传文档成功, uid: {}, kid: {}, docId: {}, filename: {}",
                    uid, kid, docId, file.getOriginalFilename());

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "文档上传成功",
                    "data", Map.of(
                            "uid", uid,
                            "kid", kid,
                            "docId", docId,
                            "filename", file.getOriginalFilename(),
                            "segments", segments.size(),
                            "fileSize", file.getSize()
                    )
            ));

        } catch (Exception e) {
            log.error("管理员上传用户文档失败, uid: {}, kid: {}, error: {}", uid, kid, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "文档上传失败: " + e.getMessage()
            ));
        }
    }

    // ============= 辅助方法 =============

    private Document parseDocument(MultipartFile file) throws IOException {
        String filename = getFileName(file);

        // 构造带有文件名的 Metadata
        Supplier<Metadata> metadataSupplier = () -> {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            return metadata;
        };

        // 使用带有 metadataSupplier 的 ApacheTikaDocumentParser 实例
        ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser(
                null,
                null,
                metadataSupplier,
                null,
                true
        );

        return parser.parse(file.getInputStream());
    }

    @NotNull
    private static String getFileName(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new NoResultException("文件名不能为空");
        }

        String[] arr = filename.toLowerCase().split("\\.");
        if (arr.length < 2) {
            throw new UnsupportedOperationException("不支持的文档格式: " + filename);
        }

        String extension = arr[arr.length - 1];
        if (!KnowLedgeEnvs.SUPPORTED_FILES.contains(extension)) {
            throw new UnsupportedOperationException("不支持的文档格式: " + filename);
        }
        return filename;
    }

    // 文本清理和分割
    private List<TextSegment> preprocessDocument(Document document) {
        String cleanedText = document.text()
                .replaceAll("[\\p{Cntrl}\\p{Space}]+", " ")
                .trim();
        List<TextSegment> segments = splitter.split(Document.document(cleanedText, document.metadata()));
        return segments;
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}