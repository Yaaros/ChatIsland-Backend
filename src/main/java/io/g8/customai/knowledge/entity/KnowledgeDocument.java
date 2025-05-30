package io.g8.customai.knowledge.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class KnowledgeDocument {
    // Getters and Setters
    private Long id;
    private String docId;
    private String uid;
    private String kid;
    private String originalFilename;
    private String fileType;
    private Long fileSize;
    private Integer segmentCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadTime;

    private Integer status;

    // 构造函数
    public KnowledgeDocument() {}

    public KnowledgeDocument(String docId, String uid, String kid, String originalFilename,
                             String fileType, Long fileSize) {
        this.docId = docId;
        this.uid = uid;
        this.kid = kid;
        this.originalFilename = originalFilename;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.status = 1;
        this.segmentCount = 0;
    }

}