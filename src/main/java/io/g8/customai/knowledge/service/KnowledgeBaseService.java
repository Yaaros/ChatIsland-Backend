package io.g8.customai.knowledge.service;

import io.g8.customai.knowledge.entity.KnowledgeBase;
import io.g8.customai.knowledge.entity.KnowledgeDocument;
import io.g8.customai.knowledge.mapper.KnowledgeBaseMapper;
import io.g8.customai.knowledge.mapper.KnowledgeDocumentMapper;
import io.g8.customai.knowledge.store.RedisEmbeddingStore;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class KnowledgeBaseService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private RedisEmbeddingStore embeddingStore;

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
                existing.setDocumentCount(0); // 也可保留原值
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
            kbInfo.put("description", kb.getDescription());  // 添加 description 字段
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
        kbInfo.put("description", kb.getDescription());  // 添加 description 字段
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
     * 添加文档到知识库
     */
    public KnowledgeDocument addDocument(String uid, String kid, String docId,
                                         String originalFilename, String fileType,
                                         Long fileSize, int segmentCount) {
        // 检查知识库是否存在
        KnowledgeBase kb = knowledgeBaseMapper.findByUidAndKid(uid, kid);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }

        // 创建文档记录
        KnowledgeDocument doc = new KnowledgeDocument(docId, uid, kid, originalFilename, fileType, fileSize);
        doc.setSegmentCount(segmentCount);

        // 保存到数据库
        knowledgeDocumentMapper.insertDocument(doc);

        // 更新知识库文档数量
        int currentCount = knowledgeDocumentMapper.countByUidAndKid(uid, kid);
        knowledgeBaseMapper.updateDocumentCount(uid, kid, currentCount);

        return doc;
    }

    /**
     * 删除特定文档
     */
    public void deleteDocument(String uid, String docId) {
        // 获取文档信息
        KnowledgeDocument doc = knowledgeDocumentMapper.findByDocId(docId);
        if (doc == null || !doc.getUid().equals(uid)) {
            throw new RuntimeException("文档不存在或无权限");
        }

        // 软删除数据库记录
        knowledgeDocumentMapper.deleteByDocId(docId);

        // 从Redis中删除向量数据
        embeddingStore.removeForUser(uid, doc.getKid(), docId);

        // 更新知识库文档数量
        int currentCount = knowledgeDocumentMapper.countByUidAndKid(uid, doc.getKid());
        knowledgeBaseMapper.updateDocumentCount(uid, doc.getKid(), currentCount);
    }

    /**
     * 删除特定知识库
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

        // 从Redis中删除所有向量数据
        embeddingStore.removeKbForUser(uid, kid);
    }

    /**
     * 删除用户所有知识库
     */
    public void deleteAllKnowledgeBases(String uid) {
        // 软删除数据库记录
        knowledgeBaseMapper.deleteAllByUid(uid);
        knowledgeDocumentMapper.deleteAllByUid(uid);

        // 从Redis中删除所有向量数据
        embeddingStore.removeAllForUser(uid);
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
     * 测试 Redis 连接
     * @return true 如果连接成功，false 如果连接失败
     */
    public boolean testRedisConnection() {
        try {
            // 使用反射访问 RedisEmbeddingStore 中的 private final JedisPooled jedis
            Field jedisField = RedisEmbeddingStore.class.getDeclaredField("jedis");
            jedisField.setAccessible(true);
            redis.clients.jedis.JedisPooled jedis = (redis.clients.jedis.JedisPooled) jedisField.get(embeddingStore);
            String result = jedis.ping();
            return "PONG".equalsIgnoreCase(result);
        } catch (JedisConnectionException e) {
            System.err.println("Redis connection failed: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error accessing JedisPooled: " + e.getMessage());
            return false;
        }
    }


}