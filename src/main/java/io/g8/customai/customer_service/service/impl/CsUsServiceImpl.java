package io.g8.customai.customer_service.service.impl;

import com.alibaba.fastjson.JSON;
import io.g8.customai.customer_service.entity.CsInquiry;
import io.g8.customai.customer_service.mapper.CsInquiryMapper;
import io.g8.customai.customer_service.service.CsAiService;
import io.g8.customai.customer_service.service.CsUsService;
import io.g8.customai.user.entity.User;
import io.g8.customai.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class CsUsServiceImpl implements CsUsService {
    @Autowired
    private CsAiService csAiService;
    @Autowired
    private CsInquiryMapper csInquiryMapper;
    @Autowired
    private Random random;
    @Autowired
    private UserMapper userMapper;
    @Override
    public Map<String, Object> userAsk(String userUid, String message) {
        String aiAnswer = csAiService.ask(message);
        LocalDateTime now = LocalDateTime.now();
        String status = (aiAnswer.contains("未找到")
                        || aiAnswer.contains("没有")
                        || aiAnswer.contains("未查到")
                        || aiAnswer.contains("联系人工")
                        || aiAnswer.contains("不在"))
                        ? "PENDING" : "REPLY_SUCCESS";

        List<Map<String, String>> history = new ArrayList<>();
        history.add(Map.of("name", "AI", "msg", aiAnswer));

        CsInquiry inquiry = new CsInquiry();
        inquiry.setUserUid(userUid);
        inquiry.setStatus(status);
        inquiry.setMessageContent(message);
        inquiry.setInquiryTime(now);
        inquiry.setReplyTime(status.equals("REPLY_SUCCESS") ? now : null);
        inquiry.setReplyHistory(JSON.toJSONString(history));  // 使用 fastjson 或 jackson

        inquiry.setAssignedCsUid(status.equals("PENDING") ? assignRandomCsUid() : null);
        csInquiryMapper.insert(inquiry);

        return Map.of("answer", aiAnswer, "status", status);
    }

    private String assignRandomCsUid() {

        List<User> list = userMapper.findByType(User.Category.CS);
        // 检查客服列表是否为空
        if (list.isEmpty()) {
            throw new RuntimeException("没有可用的客服用户");
        }
        // 使用 random 随机选择一个客服
        int randomIndex = random.nextInt(list.size());
        User selectedCs = list.get(randomIndex);
        return selectedCs.getUid();
    }

    @Override
    public List<CsInquiry> getInquiriesByUid(String userUid) {
        return csInquiryMapper.findByUserUid(userUid);
    }
}
