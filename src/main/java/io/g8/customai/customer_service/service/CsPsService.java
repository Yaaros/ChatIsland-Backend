package io.g8.customai.customer_service.service;

import io.g8.customai.customer_service.entity.CsInquiry;

import java.util.List;

public interface CsPsService {
    List<CsInquiry> getPendingInquiriesByCid(String csUid);
    boolean completeInquiry(String csUid, String inquiryId, String replyMsg);
}