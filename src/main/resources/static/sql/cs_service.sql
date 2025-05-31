CREATE TABLE cs_inquiry (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            user_uid VARCHAR(50) NOT NULL,
                            status VARCHAR(20) NOT NULL,
                            message_content TEXT NOT NULL,
                            assigned_cs_uid VARCHAR(50),
                            inquiry_time TIMESTAMP NOT NULL,
                            reply_time TIMESTAMP
);