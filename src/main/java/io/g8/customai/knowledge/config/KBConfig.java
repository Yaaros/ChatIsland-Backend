package io.g8.customai.knowledge.config;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "customai.kb")
public class KBConfig {
    private String ollamaUrl;
    private String ollamaModelEmbed;
    private String chromaUrl;
    private String chromaCollection;
    @Bean
    @Lazy
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .modelName(ollamaModelEmbed)       // 使用配置值
                .baseUrl(ollamaUrl)
                .httpClientBuilder(new JdkHttpClientBuilder())
                .maxRetries(5)
                .timeout(Duration.ofSeconds(10))
                .build();
    }
    @Bean
    @Lazy
    public ChromaEmbeddingStore embeddingStore() {
        return ChromaEmbeddingStore.builder()
                .baseUrl(chromaUrl)
                .collectionName(chromaCollection)
                .build();
    }
}

