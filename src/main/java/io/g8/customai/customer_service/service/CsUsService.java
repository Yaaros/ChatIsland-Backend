package io.g8.customai.customer_service.service;

import io.g8.customai.customer_service.entity.CsInquiry;

import java.util.*;

public interface CsUsService {
    Map<String, Object> userAsk(String userUid, String message);
    List<CsInquiry> getInquiriesByUid(String userUid);
}