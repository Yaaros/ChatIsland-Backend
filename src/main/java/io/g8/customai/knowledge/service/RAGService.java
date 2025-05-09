package io.g8.customai.knowledge.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.g8.customai.knowledge.config.RAGConfig;
import io.g8.customai.knowledge.net.QueryResponse;
import io.g8.customai.knowledge.utils.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RAGService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private RAGConfig ragConfig;

    @Autowired
    private Map<String, EmbeddingStore<TextSegment>> userEmbeddingStores;

    @Autowired
    private FileUtils fileUtils;

    private static final PromptTemplate PROMPT_TEMPLATE =  PromptTemplate.from(
            "你是一个基于知识库的智能助手。请根据以下参考内容回答用户的问题。如果参考内容中没有相关信息，请直接告知用户无法回答。\n\n" +
                    "参考内容：\n{{references}}\n\n" +
                    "用户问题：{{query}}\n\n" +
                    "请提供详细、准确、有帮助的回答："
    );

    public String processAndStoreDocument(MultipartFile file, String userId) throws IOException {
        // 1. 解析文件内容
        String content = fileUtils.extractTextFromFile(file);
        String fileName = file.getOriginalFilename();

        // 2. 创建文档对象
        Document document = Document.from(content);

//        // 2. 创建文档对象
//        Document document = new Document(
//                content,
//                Map.of(
//                        "fileName", fileName,
//                        "fileType", file.getContentType(),
//                        "userId", userId
//                )
//        );

        // 3. 文档分段
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 0);
        List<TextSegment> segments = splitter.split(document);

        // 4. 获取用户专属的嵌入存储
        // Here IOException
        EmbeddingStore<TextSegment> embeddingStore =
                ragConfig.getOrCreateEmbeddingStore(userId, userEmbeddingStores);

        // 5. 嵌入并存储每个分段
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            embeddingStore.add(embedding, segment);
        }

        return fileName;
    }

    public QueryResponse processQuery(String query, String userId) {
        // 1. 使用用户专属检索器
        EmbeddingStoreContentRetriever retriever = ragConfig.createRetrieverForUser(userId, userEmbeddingStores);

        // 2. 检索相关内容
        List<Content> contents = retriever.retrieve(Query.from(query));

        // 如果没有找到相关内容
        if (contents.isEmpty()) {
            return new QueryResponse(
                    "我的知识库中没有相关信息来回答这个问题。请尝试上传相关文档或提出其他问题。",
                    new ArrayList<>()
            );
        }

        // 3. 提取参考文本和来源
        StringBuilder referencesBuilder = new StringBuilder();
        List<String> sources = new ArrayList<>();

        for (int i = 0; i < contents.size(); i++) {
            Content content = contents.get(i);
            TextSegment segment = content.textSegment();

            // 拼接参考内容
            referencesBuilder.append("参考文档 ").append(i + 1).append(":\n");
            referencesBuilder.append(segment.text()).append("\n\n");

            // 提取元数据中的文件名
            Metadata metadata = segment.metadata();
            if (metadata.containsKey("fileName")) {
                String fileName = metadata.getString("fileName");
                if (!sources.contains(fileName)) {
                    sources.add(fileName);
                }
            }
        }

        // 4. 使用 Qwen 生成回答
        Map<String, Object> variables = Map.of(
                "references", referencesBuilder.toString(),
                "query", query
        );

        String prompt = PROMPT_TEMPLATE.apply(variables).text();

        ChatResponse response = chatLanguageModel.chat(
                new SystemMessage("你是一个基于知识库的AI助手，负责回答用户的问题。请用简洁、准确、友好的方式回答。"),
                new UserMessage(prompt)
        );

        return new QueryResponse(response.aiMessage().text(), sources);
    }

}