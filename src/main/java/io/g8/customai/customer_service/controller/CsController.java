package io.g8.customai.customer_service.controller;

import io.g8.customai.common.security.jwt.JwtUtil;
import io.g8.customai.customer_service.entity.CsInquiry;
import io.g8.customai.customer_service.service.*;
import io.g8.customai.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/help")
public class CsController {

    @Autowired private CsUsService csUsService;
    @Autowired private CsPsService csPsService;
    @Autowired private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;

    // 用户提问接口
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> request) {
        String userUid = request.get("uid");
        String message = request.get("msg");
        if (userUid == null || message == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户UID和消息内容不能为空"));
        }
        Map<String, Object> result = csUsService.userAsk(userUid, message);
        return ResponseEntity.ok(result);
    }

    // 用户查询历史
    @GetMapping("/inquiries")
    public ResponseEntity<?> getInquiries(@RequestParam("uid") String userUid) {
        if (userUid == null || userUid.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户UID不能为空"));
        }
        List<CsInquiry> inquiries = csUsService.getInquiriesByUid(userUid);
        return ResponseEntity.ok(Map.of("status", "success", "data", inquiries));
    }

    // 客服获取待处理消息
    @GetMapping("/inquiries4cs")
    public ResponseEntity<?> getCsInquiries(@RequestParam("uid") String csUid) {
        if (csUid == null || csUid.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "客服UID不能为空"));
        }
        List<CsInquiry> inquiries = csPsService.getPendingInquiriesByCid(csUid);
        return ResponseEntity.ok(Map.of("status", "success", "data", inquiries));
    }

    // 客服完成回复
    @PostMapping("/complete")
    public ResponseEntity<?> completeUserQuestion(
            @RequestHeader("Authorization") String token,
            @RequestBody HashMap<String,String> input) {
        String csUid = input.getOrDefault("csUid",jwtUtil.getUidFromToken(token.substring(7)));
        String inquiryId = input.getOrDefault("inquiryId", null);
        String replyMsg = input.getOrDefault("replyMsg", null);
        if (csUid    == null || csUid.trim().isEmpty()
          ||inquiryId== null || inquiryId.trim().isEmpty()
          ||replyMsg == null ||  replyMsg.trim().isEmpty()){
            return ResponseEntity.badRequest().body(Map.of("error", "客服id,查询id,客服回复至少存在一个NULL"));
        }
        if (csPsService.completeInquiry(csUid, inquiryId, replyMsg)) {
            String name = userService.findByUid(csUid).getName();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", String.format("问题 %s 已由客服 %s 回复", inquiryId, name)
            ));
        }
        return ResponseEntity.status(403).body(Map.of("status", "error", "data", "问题已被处理或不存在"));
    }
}
