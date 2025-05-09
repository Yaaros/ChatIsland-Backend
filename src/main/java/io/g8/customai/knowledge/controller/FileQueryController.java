package io.g8.customai.knowledge.controller;

import io.g8.customai.knowledge.net.QueryRequest;
import io.g8.customai.knowledge.net.QueryResponse;
import io.g8.customai.knowledge.service.RAGService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileQueryController {

    @Autowired
    private RAGService ragService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {
        try {
            String result = ragService.processAndStoreDocument(file, userId);
            return ResponseEntity.ok("文件上传成功: " + result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("文件上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> queryFiles(@RequestBody QueryRequest request) {
        try {
            QueryResponse response = ragService.processQuery(request.getQuery(), request.getUserId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new QueryResponse("处理查询时出错: " + e.getMessage(), null)
            );
        }
    }
}