package io.g8.customai.customer_service.ai.service;

import io.g8.customai.customer_service.entity.CsInquiry;

import java.time.LocalDateTime;
import java.util.List;

public interface CsAiService {
    String findAnswer(String message);
    void recordInquiry(String userUid, String message, String status,
                              LocalDateTime inquiryTime, LocalDateTime replyTime);
    List<CsInquiry> getInquiriesByUserUid(String userUid);
    List<CsInquiry> getInquiriesByCsUid(String csUid);
}