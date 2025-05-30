# -- 知识库表
# CREATE TABLE knowledge_base (
#                                 id BIGINT PRIMARY KEY AUTO_INCREMENT,
#                                 kid VARCHAR(50) NOT NULL COMMENT '知识库ID，格式为kb_数字',
#                                 uid VARCHAR(100) NOT NULL COMMENT '用户ID',
#                                 name VARCHAR(200) NOT NULL COMMENT '知识库名称',
#                                 tags JSON COMMENT '标签列表，存储为JSON数组',
#                                 created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
#                                 updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
#                                 stat
#                                     us TINYINT DEFAULT 1 COMMENT '状态：1-正常，0-已删除',
#                                 document_count INT DEFAULT 0 COMMENT '文档数量',
#                                 UNIQUE KEY uk_uid_kid (uid, kid),
#                                 INDEX idx_uid (uid),
#                                 INDEX idx_status (status)
# ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';
#
# -- 文档表
# CREATE TABLE knowledge_document (
#                                     id BIGINT PRIMARY KEY AUTO_INCREMENT,
#                                     doc_id VARCHAR(50) NOT NULL COMMENT '文档ID',
#                                     uid VARCHAR(100) NOT NULL COMMENT '用户ID',
#                                     kid VARCHAR(50) NOT NULL COMMENT '知识库ID',
#                                     original_filename VARCHAR(500) NOT NULL COMMENT '原始文件名',
#                                     file_type VARCHAR(20) NOT NULL COMMENT '文件类型：txt/pdf/md等',
#                                     file_size BIGINT NOT NULL COMMENT '文件大小（字节）',
#                                     segment_count INT DEFAULT 0 COMMENT '分段数量',
#                                     upload_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
#                                     status TINYINT DEFAULT 1 COMMENT '状态：1-正常，0-已删除',
#                                     UNIQUE KEY uk_doc_id (doc_id),
#                                     INDEX idx_uid_kid (uid, kid),
#                                     INDEX idx_status (status),
#                                     FOREIGN KEY (uid, kid) REFERENCES knowledge_base(uid, kid) ON DELETE CASCADE
# ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';
#
# -- 为了获取用户下一个知识库编号，创建一个序列表
# CREATE TABLE knowledge_base_sequence (
#                                          uid VARCHAR(100) PRIMARY KEY,
#                                          next_kid_num INT DEFAULT 1 COMMENT '下一个知识库编号',
#                                          updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
# ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库序列号表';

ALTER TABLE knowledge_base ADD COLUMN description TEXT;