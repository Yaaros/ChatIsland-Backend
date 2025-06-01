package io.g8.customai.knowledge.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

@Configuration
@ConfigurationProperties (prefix = "customai.kb")
public class KBConfig {
    @Value("${customai.kb.ollama.url}")
    private String ollamaUrl;
    @Value("${customai.kb.ollama.model.embed}")
    private String ollamaModelEmbed;

    @Value("${customai.kb.chroma.url}")
    private String chromaUrl;
    @Value("${customai.kb.chroma.collection}")
    private String chromaCollection;
    @Bean
    @Lazy
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .modelName("bge-m3")
                .baseUrl("http://localhost:11434")
                .maxRetries(3)
                .timeout(Duration.ofSeconds(3))
                .build();
    }
    @Bean
    @Lazy
    public ChromaEmbeddingStore embeddingStore()
    {
        return ChromaEmbeddingStore.builder()
                .baseUrl("http://localhost:8111")
                .collectionName(chromaCollection)
                .build();
    }
}
