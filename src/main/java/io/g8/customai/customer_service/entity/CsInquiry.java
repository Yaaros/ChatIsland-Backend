package io.g8.customai.customer_service.entity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Setter
@Getter
@ToString
public class CsInquiry {
    private Long id;
    private String userUid;
    private String status;
    private String messageContent;
    private String assignedCsUid;
    private LocalDateTime inquiryTime;
    private LocalDateTime replyTime;
    private String replyHistory;

}