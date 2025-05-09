package io.g8.customai.knowledge.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
//import dev.langchain4j.retriever.EmbeddingStoreRetriever;
//import dev.langchain4j.retriever.Retriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RAGConfig {

    @Value("${dashscope.api.key}")
    private String dashscopeApiKey;

    @Value("${chroma.url}")
    private String chromaUrl;

    @Bean
    public EmbeddingModel embeddingModel() {
        return QwenEmbeddingModel.builder()
                .apiKey(dashscopeApiKey)
                .modelName("text-embedding-v1")
                .build();
    }

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return QwenChatModel.builder()
                .apiKey(dashscopeApiKey)
                .modelName("qwen-turbo")
                .build();
    }

    // 创建用户专属嵌入存储的Map
    @Bean
    public Map<String, EmbeddingStore<TextSegment>> userEmbeddingStores() {
        return new HashMap<>();
    }

    // 获取或创建用户专属的Chroma嵌入存储
    public EmbeddingStore<TextSegment> getOrCreateEmbeddingStore(String userId, Map<String, EmbeddingStore<TextSegment>> stores) {
        // 第一步：检查是否已经存在该用户的 store
        if (stores.containsKey(userId)) {
            System.out.println("已存在用户: " + userId + " 的 EmbeddingStore");
            return stores.get(userId); // 如果有，直接返回
        } else {
            System.out.println("未找到用户: " + userId + " 的 EmbeddingStore，开始创建新的...");

            // 第二步：构建新的 collection 名称
            String collectionName = "user_" + userId + "_collection";
            System.out.println("即将创建的新集合名称: " + collectionName);

            // 第三步：使用 builder 构建 EmbeddingStore 实例
            ChromaEmbeddingStore.Builder builder = ChromaEmbeddingStore.builder();

            // 设置 base URL
            builder.baseUrl(chromaUrl);
            System.out.println("已设置 baseUrl: " + chromaUrl);

            // 设置集合名称
            builder.collectionName(collectionName);
            System.out.println("已设置 collectionName: " + collectionName);

            // 构建完成
            EmbeddingStore<TextSegment> newStore = builder.build();
            System.out.println("成功构建新的 EmbeddingStore");

            // 第四步：将新构建的 store 放入 map
            EmbeddingStore<TextSegment> stored = stores.put(userId, newStore);
            System.out.println("已将用户: " + userId + " 的 EmbeddingStore 存入缓存");

            // 返回新建的 store
            return newStore;
        }

//
//        return stores
//              .computeIfAbsent(userId,
//              id -> ChromaEmbeddingStore.builder()
//                          .baseUrl(chromaUrl)
//                          .collectionName("user_" + id + "_collection")
//                          .build());
    }

    // 创建用户专属检索器
    public EmbeddingStoreContentRetriever createRetrieverForUser(String userId, Map<String, EmbeddingStore<TextSegment>> stores) {
        EmbeddingStore<TextSegment> store = getOrCreateEmbeddingStore(userId, stores);
        return EmbeddingStoreContentRetriever.from(store);
    }
}