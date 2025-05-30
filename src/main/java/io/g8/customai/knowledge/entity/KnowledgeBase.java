package io.g8.customai.knowledge.entity;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@ToString
public class KnowledgeBase {
    // Getters and Setters
    private Long id;
    private String kid;
    private String uid;
    private String name;
    private List<String> tags;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    private Integer status;
    private Integer documentCount;

    // 构造函数
    public KnowledgeBase() {}

    public KnowledgeBase(String kid, String uid, String name, List<String> tags) {
        this(kid, uid, name, tags, "");
    }
    public KnowledgeBase(String kid, String uid, String name, List<String> tags,  String description) {
        this.description = description;
        this.kid = kid;
        this.uid = uid;
        this.name = name;
        this.tags = tags;
        this.status = 1;
        this.documentCount = 0;
        this.createdTime = LocalDateTime.now();
        this.updatedTime = this.createdTime;
    }

}

