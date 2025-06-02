package io.g8.customai.customer_service.service.impl;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.g8.customai.chat.config.*;
import io.g8.customai.common.constants.KnowLedgeEnvs;
import io.g8.customai.common.constants.SysEnvs;
import io.g8.customai.customer_service.service.CsAiService;
import io.g8.customai.knowledge.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;
import java.util.concurrent.*;

@Service
public class CsAiServiceImpl implements CsAiService {
    @Autowired
    private  RagService ragService;
    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private AiAssistantFactory assistantFactory;

    private NonIdAiAssistant assistant;
    @Override
    public String ask(String question) {
        assistant = (NonIdAiAssistant) assistantFactory.create(SysEnvs.CsModelName, ChatType.CS);
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(Double.valueOf(KnowLedgeEnvs.MIN_SCORE))
                .build();
        EmbeddingSearchResult<TextSegment> result = ragService.searchForUser(SysEnvs.SysUid, SysEnvs.SysKid, searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();
        if (matches.isEmpty()) {
            return "未找到相关信息，请联系人工客服。";
        }

        // 仅拼接 text 字段，并控制长度
        StringBuilder promptBuilder = new StringBuilder(
        "请根据以下内容,以客服的口吻回答问题(40字以内,如果没有相关内容请回答无法查到该信息,请联系人工客服):\n");
        for (EmbeddingMatch<TextSegment> match : matches) {
            String text = match.embedded().text();
            // 限制单条 text 长度，避免过长
            if (text.length() > 100) {
                text = text.substring(0, 100) + "...";
            }
            promptBuilder.append(text).append("\n");
        }
        promptBuilder.append("问题：").append(question);

        String prompt = promptBuilder.toString();
        System.out.println(prompt);
        // 添加超时机制
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> assistant.chat(prompt));
        try {
            String response = future.get(15, TimeUnit.SECONDS); // 10秒超时
            return response.length() > 40 ? response.substring(0, 40) : response;
        } catch (TimeoutException e) {
            return "请求超时，请联系人工客服。";
        } catch (Exception e) {
            return "系统错误，请联系人工客服。";
        } finally {
            executor.shutdown();
        }
    }
}