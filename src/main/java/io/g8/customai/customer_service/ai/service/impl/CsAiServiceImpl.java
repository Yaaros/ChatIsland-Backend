package io.g8.customai.customer_service.ai.service.impl;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.g8.customai.common.constants.KnowLedgeEnvs;
import io.g8.customai.customer_service.ai.service.CsAiService;
import io.g8.customai.customer_service.entity.CsInquiry;
import io.g8.customai.customer_service.mapper.CsInquiryMapper;
import io.g8.customai.knowledge.deprecated.store.RedisEmbeddingStore;
import io.g8.customai.user.entity.User;
import io.g8.customai.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import dev.langchain4j.data.segment.TextSegment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class CsAiServiceImpl implements CsAiService {
    @Autowired
    private RedisEmbeddingStore embeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private CsInquiryMapper csInquiryMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private Random random;

    public String findAnswer(String message) {
        String kid = "kb_1";
        String uid = "1";

        // 使用 KnowledgeBaseControllerOld 中的搜索逻辑
        Embedding queryEmbedding = embeddingModel.embed(message).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(Double.valueOf(KnowLedgeEnvs.MIN_SCORE))
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.searchForUser(uid, kid, searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();
        matches.forEach(ans->{
            System.out.println(ans.score());
        });
        if (matches.isEmpty()) {
            return "未找到相关答案";
        }

        // 返回得分最高的答案
        return matches.get(0).embedded().text();
    }

    public void recordInquiry(String userUid, String message, String status,
                              LocalDateTime inquiryTime, LocalDateTime replyTime) {
        String assignedCsUid = null;
        if(status.equals("PENDING")){
            // 获取所有客服用户
            List<User> list = userMapper.findByType(User.Category.CS);
            // 检查客服列表是否为空
            if (list.isEmpty()) {
                throw new RuntimeException("没有可用的客服用户");
            }
            // 使用 random 随机选择一个客服
            int randomIndex = random.nextInt(list.size());
            User selectedCs = list.get(randomIndex);
            assignedCsUid = selectedCs.getUid();
        }
        // 创建并设置 CsInquiry 对象
        CsInquiry inquiry = new CsInquiry();
        inquiry.setUserUid(userUid);
        inquiry.setStatus(status);
        inquiry.setMessageContent(message);
        inquiry.setAssignedCsUid(assignedCsUid); // 分配随机选中的客服 UID
        inquiry.setInquiryTime(inquiryTime);
        inquiry.setReplyTime(replyTime);

        // 插入数据库
        csInquiryMapper.insert(inquiry);
    }

    public List<CsInquiry> getInquiriesByUserUid(String userUid) {
        return csInquiryMapper.findByUserUid(userUid);
    }

    public List<CsInquiry> getInquiriesByCsUid(String csUid) {
        return csInquiryMapper.findByAssignedCsUid(csUid);
    }

}