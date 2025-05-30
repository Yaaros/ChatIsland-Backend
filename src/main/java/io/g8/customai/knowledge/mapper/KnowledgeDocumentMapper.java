package io.g8.customai.knowledge.mapper;

import io.g8.customai.knowledge.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper {

    // 插入文档记录
    @Insert("INSERT INTO knowledge_document (doc_id, uid, kid, original_filename, file_type, file_size, segment_count, status) " +
            "VALUES (#{docId}, #{uid}, #{kid}, #{originalFilename}, #{fileType}, #{fileSize}, #{segmentCount}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDocument(KnowledgeDocument document);

    // 查询知识库下的所有文档
    @Select("SELECT * FROM knowledge_document WHERE uid = #{uid} AND kid = #{kid} AND status = 1 ORDER BY upload_time DESC")
    List<KnowledgeDocument> findByUidAndKid(@Param("uid") String uid, @Param("kid") String kid);

    // 查询用户所有文档
    @Select("SELECT * FROM knowledge_document WHERE uid = #{uid} AND status = 1 ORDER BY upload_time DESC")
    List<KnowledgeDocument> findByUid(String uid);

    // 根据文档ID查询
    @Select("SELECT * FROM knowledge_document WHERE doc_id = #{docId} AND status = 1")
    KnowledgeDocument findByDocId(String docId);

    // 删除文档（软删除）
    @Update("UPDATE knowledge_document SET status = 0 WHERE doc_id = #{docId}")
    int deleteByDocId(String docId);

    // 删除知识库下所有文档（软删除）
    @Update("UPDATE knowledge_document SET status = 0 WHERE uid = #{uid} AND kid = #{kid}")
    int deleteAllByUidAndKid(@Param("uid") String uid, @Param("kid") String kid);

    // 删除用户所有文档（软删除）
    @Update("UPDATE knowledge_document SET status = 0 WHERE uid = #{uid}")
    int deleteAllByUid(String uid);

    // 统计知识库文档数量
    @Select("SELECT COUNT(*) FROM knowledge_document WHERE uid = #{uid} AND kid = #{kid} AND status = 1")
    int countByUidAndKid(@Param("uid") String uid, @Param("kid") String kid);

    // 更新分段数量
    @Update("UPDATE knowledge_document SET segment_count = #{segmentCount} WHERE doc_id = #{docId}")
    int updateSegmentCount(@Param("docId") String docId, @Param("segmentCount") int segmentCount);
}