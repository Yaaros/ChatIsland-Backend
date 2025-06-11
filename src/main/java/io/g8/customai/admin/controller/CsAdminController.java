package io.g8.customai.admin.controller;

import io.g8.customai.admin.util.Utils;
import io.g8.customai.common.security.jwt.JwtUtil;
import io.g8.customai.common.security.utils.Util;
import io.g8.customai.customer_service.entity.CsInquiry;
import io.g8.customai.customer_service.service.CsPsService;
import io.g8.customai.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/cs")
public class CsAdminController {
    private static final Logger log = LoggerFactory.getLogger(CsAdminController.class);
    @Autowired
    private CsPsService csPsService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserService userService;
    @GetMapping("/inquiries/{cid}")
    public ResponseEntity<?> getCsInquiries(
            @PathVariable String cid,
            @RequestHeader("Authorization") String authHeader
    ) {
        if (!Utils.validateAdminRole(authHeader,jwtUtil,log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }
        if (cid == null || cid.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "客服UID不能为空"));
        }
        List<CsInquiry> inquiries = csPsService.getPendingInquiriesByCid(cid);
        return ResponseEntity.ok(Map.of("status", "success", "data", inquiries));
    }

    // 客服完成回复
    @PostMapping("/complete")
    public ResponseEntity<?> completeUserQuestion(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody HashMap<String,String> input) {
        if (!Utils.validateAdminRole(authHeader,jwtUtil,log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }
        String csUid = input.getOrDefault("csUid",
                       jwtUtil.getUidFromToken(authHeader.substring(7)));
        String inquiryId = input.getOrDefault("inquiryId", null);
        String replyMsg = input.getOrDefault("replyMsg", null);
        if (csUid    == null || csUid.trim().isEmpty()
          ||inquiryId== null || inquiryId.trim().isEmpty()
          ||replyMsg == null ||  replyMsg.trim().isEmpty()) {
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
