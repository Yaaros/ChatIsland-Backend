package io.g8.customai.knowledge.mapper;

// KnowledgeBaseMapper.java
import io.g8.customai.knowledge.entity.KnowledgeBase;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface KnowledgeBaseMapper {

    // 插入知识库
    @Insert("INSERT INTO knowledge_base (kid, uid, name, tags, description, status, document_count) " +
            "VALUES (#{kid}, #{uid}, #{name}, #{tags,typeHandler=io.g8.customai.knowledge.utils.JsonTypeHandler}, #{description}, #{status}, #{documentCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertKnowledgeBase(KnowledgeBase knowledgeBase);

    // 查询用户的所有知识库
    @Select("SELECT * FROM knowledge_base WHERE uid = #{uid} AND status = 1 ORDER BY created_time DESC")
    @Results({
            @Result(property = "tags", column = "tags",
                    typeHandler = io.g8.customai.knowledge.utils.JsonTypeHandler.class)
    })
    List<KnowledgeBase> findByUid(String uid);

    // 根据uid和kid查询知识库
    @Select("SELECT * FROM knowledge_base WHERE uid = #{uid} AND kid = #{kid} AND status = 1")
    @Results({
            @Result(property = "tags", column = "tags",
                    typeHandler = io.g8.customai.knowledge.utils.JsonTypeHandler.class)
    })
    KnowledgeBase findByUidAndKid(@Param("uid") String uid, @Param("kid") String kid);

    // 删除知识库（软删除）
    @Update("UPDATE knowledge_base SET status = 0 WHERE uid = #{uid} AND kid = #{kid}")
    int deleteByUidAndKid(@Param("uid") String uid, @Param("kid") String kid);

    // 删除用户所有知识库（软删除）
    @Update("UPDATE knowledge_base SET status = 0 WHERE uid = #{uid}")
    int deleteAllByUid(String uid);

    // 更新文档数量
    @Update("UPDATE knowledge_base SET document_count = #{count} WHERE uid = #{uid} AND kid = #{kid}")
    int updateDocumentCount(@Param("uid") String uid, @Param("kid") String kid, @Param("count") int count);

    // 获取用户下一个知识库编号
    @Select("SELECT next_kid_num FROM knowledge_base_sequence WHERE uid = #{uid}")
    Integer getNextKidNum(String uid);

    // 插入或更新序列号
    @Insert("INSERT INTO knowledge_base_sequence (uid, next_kid_num) VALUES (#{uid}, #{nextNum}) " +
            "ON DUPLICATE KEY UPDATE next_kid_num = #{nextNum}")
    int upsertSequence(@Param("uid") String uid, @Param("nextNum") int nextNum);
}

