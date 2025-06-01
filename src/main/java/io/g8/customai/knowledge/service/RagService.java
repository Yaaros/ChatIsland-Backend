package io.g8.customai.knowledge.service;
import io.g8.customai.knowledge.entity.KnowledgeBase;
import io.g8.customai.knowledge.entity.KnowledgeDocument;
import io.g8.customai.knowledge.mapper.KnowledgeBaseMapper;
import io.g8.customai.knowledge.mapper.KnowledgeDocumentMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class RagService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    public KnowledgeBase createKnowledgeBase(String uid, String name, List<String> tags) {
        // 获取下一个 kid
        Integer nextNum = knowledgeBaseMapper.getNextKidNum(uid);
        if (nextNum == null) {
            nextNum = 1;
        }

        String kid = "kb_" + nextNum;

        // 检查是否已有该 kid 的记录
        KnowledgeBase existing = knowledgeBaseMapper.findAnyByUidAndKid(uid, kid);
        if (existing != null) {
            if (existing.getStatus() == 0) {
                // 已软删除，尝试恢复
                existing.setName(name);
                existing.setTags(tags);
                existing.setStatus(1);
                existing.setCreatedTime(LocalDateTime.now());
                existing.setDocumentCount(0);
                knowledgeBaseMapper.recoverDeletedKnowledgeBase(existing);
                return existing;
            } else {
                // 已存在有效记录，抛出异常
                throw new RuntimeException("知识库已存在，kid=" + kid + "，请勿重复创建");
            }
        }

        // 构建新知识库
        KnowledgeBase kb = new KnowledgeBase();
        kb.setKid(kid);
        kb.setUid(uid);
        kb.setName(name);
        kb.setTags(tags);
        kb.setStatus(1);
        kb.setDocumentCount(0);
        kb.setCreatedTime(LocalDateTime.now());

        // 插入并推进序列号
        knowledgeBaseMapper.insertKnowledgeBase(kb);
        knowledgeBaseMapper.upsertSequence(uid, nextNum + 1);

        return kb;
    }

    public KnowledgeBase createKnowledgeBaseWithDescription(String uid, String name, List<String> tags, String description) {
        Integer nextNum = knowledgeBaseMapper.getNextKidNum(uid);
        if (nextNum == null) {
            nextNum = 1;
        }

        String kid = "kb_" + nextNum;

        KnowledgeBase existing = knowledgeBaseMapper.findAnyByUidAndKid(uid, kid);
        if (existing != null) {
            if (existing.getStatus() == 0) {
                existing.setName(name);
                existing.setTags(tags);
                existing.setDescription(description);
                existing.setStatus(1);
                existing.setCreatedTime(LocalDateTime.now());
                existing.setDocumentCount(0);
                knowledgeBaseMapper.recoverDeletedKnowledgeBase(existing);
                return existing;
            } else {
                throw new RuntimeException("知识库已存在，kid=" + kid + "，请勿重复创建");
            }
        }

        KnowledgeBase kb = new KnowledgeBase();
        kb.setKid(kid);
        kb.setUid(uid);
        kb.setName(name);
        kb.setTags(tags);
        kb.setDescription(description);
        kb.setStatus(1);
        kb.setDocumentCount(0);
        kb.setCreatedTime(LocalDateTime.now());

        knowledgeBaseMapper.insertKnowledgeBase(kb);
        knowledgeBaseMapper.upsertSequence(uid, nextNum + 1);

        return kb;
    }

    /**
     * 获取用户的所有知识库及其详细信息
     */
    public List<Map<String, Object>> getUserKnowledgeBases(String uid) {
        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.findByUid(uid);
        List<Map<String, Object>> result = new ArrayList<>();

        for (KnowledgeBase kb : knowledgeBases) {
            Map<String, Object> kbInfo = new HashMap<>();
            kbInfo.put("kid", kb.getKid());
            kbInfo.put("name", kb.getName());
            kbInfo.put("tags", kb.getTags());
            kbInfo.put("description", kb.getDescription());
            kbInfo.put("createdTime", kb.getCreatedTime());
            kbInfo.put("updatedTime", kb.getUpdatedTime());
            kbInfo.put("documentCount", kb.getDocumentCount());

            // 获取文档列表
            List<KnowledgeDocument> documents = knowledgeDocumentMapper.findByUidAndKid(uid, kb.getKid());
            List<Map<String, Object>> docList = new ArrayList<>();

            for (KnowledgeDocument doc : documents) {
                Map<String, Object> docInfo = new HashMap<>();
                docInfo.put("docId", doc.getDocId());
                docInfo.put("originalFilename", doc.getOriginalFilename());
                docInfo.put("fileType", doc.getFileType());
                docInfo.put("fileSize", doc.getFileSize());
                docInfo.put("segmentCount", doc.getSegmentCount());
                docInfo.put("uploadTime", doc.getUploadTime());
                docInfo.put("status", doc.getStatus() == 1 ? "active" : "deleted");
                docList.add(docInfo);
            }

            kbInfo.put("documents", docList);
            result.add(kbInfo);
        }

        return result;
    }

    /**
     * 获取特定知识库信息
     */
    public Map<String, Object> getKnowledgeBaseInfo(String uid, String kid) {
        KnowledgeBase kb = knowledgeBaseMapper.findByUidAndKid(uid, kid);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }

        Map<String, Object> kbInfo = new HashMap<>();
        kbInfo.put("kid", kb.getKid());
        kbInfo.put("name", kb.getName());
        kbInfo.put("tags", kb.getTags());
        kbInfo.put("description", kb.getDescription());
        kbInfo.put("createdTime", kb.getCreatedTime());
        kbInfo.put("updatedTime", kb.getUpdatedTime());
        kbInfo.put("documentCount", kb.getDocumentCount());

        // 获取文档列表
        List<KnowledgeDocument> documents = knowledgeDocumentMapper.findByUidAndKid(uid, kid);
        List<Map<String, Object>> docList = new ArrayList<>();

        for (KnowledgeDocument doc : documents) {
            Map<String, Object> docInfo = new HashMap<>();
            docInfo.put("docId", doc.getDocId());
            docInfo.put("originalFilename", doc.getOriginalFilename());
            docInfo.put("fileType", doc.getFileType());
            docInfo.put("fileSize", doc.getFileSize());
            docInfo.put("segmentCount", doc.getSegmentCount());
            docInfo.put("uploadTime", doc.getUploadTime());
            docInfo.put("status", doc.getStatus() == 1 ? "active" : "deleted");
            docList.add(docInfo);
        }

        kbInfo.put("documents", docList);
        return kbInfo;
    }

    /**
     * 添加文档到知识库（存储向量到Chroma）
     */
    public KnowledgeDocument addDocument(String uid, String kid, String docId,
                                         String originalFilename, String fileType,
                                         Long fileSize, List<Embedding> embeddings,
                                         List<TextSegment> segments) {
        // 检查知识库是否存在
        KnowledgeBase kb = knowledgeBaseMapper.findByUidAndKid(uid, kid);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }

        // 为每个文本段添加元数据标识
        List<TextSegment> enhancedSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            // 创建增强的元数据
            Map<String, Object> metadata = new HashMap<>();
            if (segment.metadata() != null) {
                metadata.putAll(segment.metadata().toMap());
            }
            metadata.put("uid", uid);
            metadata.put("kid", kid);
            metadata.put("docId", docId);
            metadata.put("segmentIndex", i);
            metadata.put("originalFilename", originalFilename);

            enhancedSegments.add(TextSegment.from(segment.text(), dev.langchain4j.data.document.Metadata.from(metadata)));
        }

        // 存储到Chroma
        embeddingStore.addAll(embeddings, enhancedSegments);

        // 创建文档记录
        KnowledgeDocument doc = new KnowledgeDocument(docId, uid, kid, originalFilename, fileType, fileSize);
        doc.setSegmentCount(segments.size());

        // 保存到数据库
        knowledgeDocumentMapper.insertDocument(doc);

        // 更新知识库文档数量
        int currentCount = knowledgeDocumentMapper.countByUidAndKid(uid, kid);
        knowledgeBaseMapper.updateDocumentCount(uid, kid, currentCount);

        return doc;
    }

    /**
     * 删除特定文档（从Chroma删除向量）
     */
    public void deleteDocument(String uid, String docId) {
        // 获取文档信息
        KnowledgeDocument doc = knowledgeDocumentMapper.findByDocId(docId);
        if (doc == null || !doc.getUid().equals(uid)) {
            throw new RuntimeException("文档不存在或无权限");
        }

        // 软删除数据库记录
        knowledgeDocumentMapper.deleteByDocId(docId);

        // 从Chroma中删除向量数据（通过docId过滤）
        Filter docFilter = new IsEqualTo("docId", docId);
        try {
            embeddingStore.removeAll(docFilter);
        } catch (Exception e) {
            System.err.println("从Chroma删除文档向量失败: " + e.getMessage());
            // 记录日志但不中断流程，因为数据库记录已经删除了
        }

        // 更新知识库文档数量
        int currentCount = knowledgeDocumentMapper.countByUidAndKid(uid, doc.getKid());
        knowledgeBaseMapper.updateDocumentCount(uid, doc.getKid(), currentCount);
    }

    /**
     * 删除特定知识库（从Chroma删除所有相关向量）
     */
    public void deleteKnowledgeBase(String uid, String kid) {
        // 检查知识库是否存在
        KnowledgeBase kb = knowledgeBaseMapper.findByUidAndKid(uid, kid);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }

        // 软删除数据库中的知识库和文档记录
        knowledgeBaseMapper.deleteByUidAndKid(uid, kid);
        knowledgeDocumentMapper.deleteAllByUidAndKid(uid, kid);

        // 从Chroma中删除所有向量数据（通过uid和kid过滤）
        Filter kbFilter = new And(
                new IsEqualTo("uid", uid),
                new IsEqualTo("kid", kid)
        );
        try {
            embeddingStore.removeAll(kbFilter);
        } catch (Exception e) {
            System.err.println("从Chroma删除知识库向量失败: " + e.getMessage());
            // 记录日志但不中断流程
        }
    }

    /**
     * 删除用户所有知识库（从Chroma删除用户所有向量）
     */
    public void deleteAllKnowledgeBases(String uid) {
        // 软删除数据库记录
        knowledgeBaseMapper.deleteAllByUid(uid);
        knowledgeDocumentMapper.deleteAllByUid(uid);

        // 从Chroma中删除所有向量数据（通过uid过滤）
        Filter userFilter = new IsEqualTo("uid", uid);
        try {
            embeddingStore.removeAll(userFilter);
        } catch (Exception e) {
            System.err.println("从Chroma删除用户所有向量失败: " + e.getMessage());
            // 记录日志但不中断流程
        }
    }

    /**
     * 检查知识库是否存在
     */
    public boolean knowledgeBaseExists(String uid, String kid) {
        return knowledgeBaseMapper.findByUidAndKid(uid, kid) != null;
    }

    /**
     * 检查文档是否属于用户
     */
    public boolean documentBelongsToUser(String uid, String docId) {
        KnowledgeDocument doc = knowledgeDocumentMapper.findByDocId(docId);
        return doc != null && doc.getUid().equals(uid);
    }

    /**
     * 测试Chroma连接
     */
    public boolean testChromaConnection() {
        try {
            // 尝试进行一个简单的查询来测试连接
            Embedding testEmbedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
            embeddingStore.search(dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                    .queryEmbedding(testEmbedding)
                    .maxResults(1)
                    .build());
            return true;
        } catch (Exception e) {
            System.err.println("Chroma connection test failed: " + e.getMessage());
            return false;
        }
    }
}