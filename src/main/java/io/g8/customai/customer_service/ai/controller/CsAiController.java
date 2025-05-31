package io.g8.customai.customer_service.ai.controller;

import io.g8.customai.customer_service.entity.CsInquiry;
import io.g8.customai.customer_service.ai.service.CsAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/help")
public class CsAiController {

    @Autowired
    private CsAiService csAiService;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> request) {
        String userUid =
                request.get("uid");
        String message = request.get("msg");

        if (userUid == null || message == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户UID和消息内容不能为空"));
        }

        String answer = csAiService.findAnswer(message);
        String status;
        LocalDateTime inquiryTime = LocalDateTime.now();
        LocalDateTime replyTime = null;

        if (answer.contains("没有") || answer.contains("未找到")) {
            status = "PENDING"; // AI 未找到答案，转人工
        } else {
            status = "REPLY_SUCCESS"; // AI 已找到答案
            replyTime = LocalDateTime.now();
        }

        csAiService.recordInquiry(userUid, message, status, inquiryTime, replyTime);

        return ResponseEntity.ok(Map.of(
                "answer", answer,
                "status", status
        ));
    }

    @GetMapping("/inquiries")
    public ResponseEntity<?> getInquiries(@RequestParam("uid") String userUid) {
        if (userUid == null || userUid.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户UID不能为空"));
        }

        List<CsInquiry> inquiries = csAiService.getInquiriesByUserUid(userUid);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "data", inquiries
        ));
    }

    @GetMapping("/inquiries4cs")
    public ResponseEntity<?> getCsInquiries(@RequestParam("uid") String csUid) {
        if (csUid == null || csUid.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "客服UID不能为空"));
        }

        List<CsInquiry> inquiries = csAiService.getInquiriesByCsUid(csUid);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "data", inquiries
        ));
    }
}