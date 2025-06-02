CREATE TABLE chat_memory (
                             memory_id VARCHAR(255) PRIMARY KEY,
                             messages TEXT NOT NULL,
                             created_time DATETIME NOT NULL,
                             updated_time DATETIME NOT NULL
);
CREATE INDEX idx_chat_memory_updated_time ON chat_memory(updated_time);

ALTER TABLE chat_memory
    ADD COLUMN session_name VARCHAR(255) DEFAULT NULL;