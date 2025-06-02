package io.g8.customai.customer_service.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import io.g8.customai.customer_service.entity.CsInquiry;
import io.g8.customai.customer_service.mapper.CsInquiryMapper;
import io.g8.customai.customer_service.service.CsPsService;
import io.g8.customai.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CsPsServiceImpl implements CsPsService {
    @Autowired
    private CsInquiryMapper csInquiryMapper;
    @Autowired
    private UserService userService;
    @Override
    public List<CsInquiry> getPendingInquiriesByCid(String csUid) {
        return csInquiryMapper.findByAssignedCsUid(csUid);
    }

    @Override
    public boolean completeInquiry(String csUid, String inquiryId, String replyMsg) {

        List<CsInquiry> list = csInquiryMapper.findPendingInquiresByMid(inquiryId);
        if (list.isEmpty()) return false;

        CsInquiry inquiry = list.get(0);
        List<Map<String, String>> history = parseJsonHistory(inquiry.getReplyHistory());
        String csName = userService.findByUid(csUid).getName();
        history.add(Map.of("name", csName, "msg", replyMsg));


        csInquiryMapper.updateReplyHistory(inquiry.getId(), JSON.toJSONString(history));
        csInquiryMapper.updateStatusToReplySuccess(inquiry.getId(), LocalDateTime.now());

        return true;
    }

    private List<Map<String, String>> parseJsonHistory(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        return JSON.parseObject(json, new TypeReference<List<Map<String, String>>>(){});
    }

}
