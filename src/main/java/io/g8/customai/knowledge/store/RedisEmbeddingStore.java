package io.g8.customai.knowledge.store;

import com.google.gson.Gson;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.g8.customai.common.constants.KnowLedgeEnvs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.*;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

@Component
public class RedisEmbeddingStore {
    private static final String VECTOR_FIELD = "embedding";
    private static final int VECTOR_DIM = 1536; // 根据实际向量维度设置
    private static final String VECTOR_ALGO = "HNSW";
    private static final String DISTANCE_METRIC = "COSINE";
    private final JedisPooled jedis;
    @Autowired
    public RedisEmbeddingStore(@Qualifier("embed-jedis") JedisPooled jedis) {
        this.jedis = jedis;
    }
    // 生成索引名称
    private String getIndexName(String uid, String kbIndex) {
        return String.format("idx:user:%s:kb:%s", uid, kbIndex);
    }

    // 生成文档 Redis 键
    private String getDocKey(String uid, String kbIndex, String id) {
        return String.format("user:%s:kb:%s:doc:%s", uid, kbIndex, id);
    }

    // 生成索引前缀
    private String getPrefix(String uid, String kbIndex) {
        return String.format("user:%s:kb:%s:doc:", uid, kbIndex);
    }

    // 生成用户所有知识库的模式
    private String getUserPattern(String uid) {
        return String.format("user:%s:kb:*:doc:*", uid);
    }

    // 生成特定知识库的模式
    private String getKbPattern(String uid, String kbIndex) {
        return String.format("user:%s:kb:%s:doc:*", uid, kbIndex);
    }

    // 创建索引（如果不存在）
    public void createIndexIfNotExists(String indexName, String prefix) {
        try {
            jedis.ftInfo(indexName); // 会抛异常说明不存在
        } catch (Exception e) {
//            Schema schema = new Schema()
//                    .addTextField("text", 1.0)
//                    .addTextField("metadata", 1.0)
//                    .addVectorField(VECTOR_FIELD, Schema.VectorField.VectorAlgo.valueOf(VECTOR_ALGO),
//                            Map.of(
//                                    "TYPE", "FLOAT32",
//                                    "DIM", VECTOR_DIM,
//                                    "DISTANCE_METRIC", DISTANCE_METRIC
//                            )
//                    );
            SchemaField[] schemaFields = new SchemaField[] {
                    TextField.of("text"),
                    TextField.of("metadata"),
                    VectorField.builder().fieldName(VECTOR_FIELD)
                                         .algorithm(VectorField.VectorAlgorithm.HNSW)
                                         .attributes(
                                          Map.of(
                                                  "TYPE", "FLOAT32",
                                                  "DIM", VECTOR_DIM,
                                                  "DISTANCE_METRIC", DISTANCE_METRIC
                                          )
                                          ).build()
            };
            try{
                jedis.ftCreate(indexName,
                        FTCreateParams.createParams()
                                .on(IndexDataType.HASH)
                                .prefix(prefix),
                        schemaFields);
            }catch (Exception ex){
                if(ex instanceof JedisDataException){
                    jedis.ftCreate(indexName,
                            FTCreateParams.createParams()
                                    .on(IndexDataType.HASH)
                                    .prefix(prefix),
                            schemaFields);
                }
            }

        }
    }
    public void add(String uid, String kbIndex, String id, String text, String metadata, List<Float> vector) {
        String indexName = getIndexName(uid, kbIndex);
        String prefix = getPrefix(uid, kbIndex);
        createIndexIfNotExists(indexName, prefix);

        String key = getDocKey(uid, kbIndex, id);

        // Store string fields (text and metadata)
        Map<String, String> stringFields = new HashMap<>();
        stringFields.put("text", text);
        stringFields.put("metadata", metadata);
        jedis.hset(key, "metadata",new Gson().toJson(stringFields));
        // Store vector as raw binary data
        byte[] vectorBytes = floatListToBytes(vector);
        jedis.hset(key.getBytes(), VECTOR_FIELD.getBytes(), vectorBytes);
    }

    public void addAllForUser(String uid, String kid, List<Embedding> embeddings, List<TextSegment> segments) {
        // 参数校验
        if (embeddings.size() != segments.size()) {
            throw new IllegalArgumentException("Embedding 和 Segment 不匹配");
        }

        // 批量处理
        for (int i = 0; i < embeddings.size(); i++) {
            String docId = UUID.randomUUID().toString();
            String text = segments.get(i).text();
            String metadata = segments.get(i).metadata() != null ?
                    segments.get(i).metadata().toMap().toString() : "{}";
            embeddings.get(i).normalize();
            List<Float> vector = embeddings.get(i).vectorAsList();
            add(uid, kid, docId, text, metadata, vector);
        }
    }
    // 向量搜索
    public List<Map<String, Object>> search(String uid, String kbIndex, EmbeddingSearchRequest request) {
        String indexName = getIndexName(uid, kbIndex);
        String prefix = getPrefix(uid, kbIndex);
        createIndexIfNotExists(indexName, prefix);
        Embedding embedding = request.queryEmbedding();
        embedding.normalize();
        String queryVectorB64 = bytesToBase64(floatListToBytes(embedding.vectorAsList()));
        String queryStr =
                String.format("*=>[KNN %d @%s $vector AS score]", request.maxResults(), VECTOR_FIELD);

        Query query = new Query(queryStr)
                .addParam("vector", base64ToBytes(queryVectorB64))
                .returnFields("text", "metadata", "score")
                .setSortBy("score", true)
                .dialect(2);

        SearchResult result = jedis.ftSearch(indexName, query);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Document doc : result.getDocuments()) {
            if(!doc.hasProperty("text"))continue;
            Map<String, Object> map = new HashMap<>();
            map.put("text", doc.getString("text"));
            map.put("metadata", doc.hasProperty("metadata")?doc.getString("metadata"):"无metadata");
            map.put("score", doc.hasProperty("score")?doc.getString("score"):"无分数");
            results.add(map);
        }
        return results;
    }

    // LangChain4J搜索接口适配
    public EmbeddingSearchResult<TextSegment> searchForUser(String uid, String kid, EmbeddingSearchRequest request) {
        List<Map<String, Object>> rawResults = search(uid, kid, request);
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

        for (Map<String, Object> result : rawResults) {
            String text = (String) result.get("text");
            String metadataStr = (String) result.get("metadata");
            double score = Double.parseDouble((String) result.get("score"));

            if (score >= Double.parseDouble(KnowLedgeEnvs.MIN_SCORE)) {
                // 简单解析metadata，实际情况可能需要更复杂的解析
                Metadata metadata = Metadata.from(Map.of("raw", metadataStr));
                TextSegment segment = TextSegment.from(text, metadata);
                matches.add(new EmbeddingMatch<>(score, kid, null, segment));
            }
        }
        return new EmbeddingSearchResult<>(matches);
    }

    // 删除单个文档
    public void removeForUser(String uid, String kid, String id) {
        String redisKey = getDocKey(uid, kid, id);
        jedis.del(redisKey);
    }

    // 删除特定知识库的所有文档
    public void removeKbForUser(String uid, String kid) {
        String pattern = getKbPattern(uid, kid);
        Set<String> keys = jedis.keys(pattern);   //这句话

        if (!keys.isEmpty()) {
            jedis.del(keys.toArray(new String[0]));
        }

        // 删除对应的索引
        String indexName = getIndexName(uid, kid);
        try {
            jedis.ftDropIndex(indexName);
        } catch (Exception e) {
            // 索引可能不存在，忽略错误
        }
    }

    // 删除用户的所有知识库
    public void removeAllForUser(String uid) {
        String pattern = getUserPattern(uid);
        Set<String> keys = jedis.keys(pattern);

        if (!keys.isEmpty()) {
            jedis.del(keys.toArray(new String[0]));
        }

        // 删除所有相关索引
        String indexPattern = String.format("idx:user:%s:kb:*", uid);
        Set<String> indexNames = jedis.keys(indexPattern);

        for (String indexName : indexNames) {
            try {
                jedis.ftDropIndex(indexName);
            } catch (Exception e) {
                // 索引可能不存在，忽略错误
            }
        }
    }

    // 获取知识库中的文档数量
    public int getDocumentCount(String uid, String kid) {
        String pattern = getKbPattern(uid, kid);
        Set<String> keys = jedis.keys(pattern);
        return keys.size();
    }

    // 获取知识库中的所有文档ID
    public List<String> getDocumentIds(String uid, String kid) {
        String pattern = getKbPattern(uid, kid);
        Set<String> keys = jedis.keys(pattern);
        List<String> docIds = new ArrayList<>();

        String prefix = getPrefix(uid, kid);
        for (String key : keys) {
            if (key.startsWith(prefix)) {
                String docId = key.substring(prefix.length());
                docIds.add(docId);
            }
        }
        return docIds;
    }
    // List<Float> 转 byte[]
    private byte[] floatListToBytes(List<Float> floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.size() * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    // byte[] 转 Base64 字符串（用于存储到Redis字符串字段）
    private String bytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Base64 字符串转 byte[]
    private byte[] base64ToBytes(String base64) {
        return Base64.getDecoder().decode(base64);
    }
}