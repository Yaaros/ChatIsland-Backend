package io.g8.customai.customer_service.mapper;

import io.g8.customai.customer_service.entity.CsInquiry;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CsInquiryMapper {

    @Insert("INSERT INTO cs_inquiry (user_uid, status, message_content, assigned_cs_uid, inquiry_time, reply_time, reply_history) " +
            "VALUES (#{userUid}, #{status}, #{messageContent}, #{assignedCsUid}, #{inquiryTime}, #{replyTime}, #{replyHistory})")
    void insert(CsInquiry inquiry);

    @Select("SELECT * FROM cs_inquiry WHERE user_uid = #{userUid}")
    List<CsInquiry> findByUserUid(@Param("userUid") String userUid);

    @Select("SELECT * FROM cs_inquiry WHERE user_uid = #{userUid} AND status = 'PENDING'")
    List<CsInquiry> findPendingInquiresByUserUid(@Param("userUid") String userUid);

    @Select("SELECT * FROM cs_inquiry WHERE assigned_cs_uid = #{csUid}")
    List<CsInquiry> findByAssignedCsUid(@Param("csUid") String csUid);

    @Select("SELECT * FROM cs_inquiry WHERE assigned_cs_uid = #{csUid} AND status = 'PENDING'")
    List<CsInquiry> findPendingInquiresByCsUid(@Param("csUid") String csUid);

    @Select("SELECT * FROM cs_inquiry WHERE id = #{messageId}")
    List<CsInquiry> findByMid(@Param("messageId") String messageId);

    @Select("SELECT * FROM cs_inquiry WHERE id = #{messageId} AND status = 'PENDING'")
    List<CsInquiry> findPendingInquiresByMid(@Param("messageId") String messageId);

    @Update("UPDATE cs_inquiry SET status = 'REPLY_SUCCESS', reply_time = #{replyTime} WHERE id = #{id}")
    void updateStatusToReplySuccess(@Param("id") Long id, @Param("replyTime") LocalDateTime time);

    @Update("UPDATE cs_inquiry SET reply_history = #{replyHistory} WHERE id = #{id}")
    void updateReplyHistory(@Param("id") Long id, @Param("replyHistory") String replyHistory);
}

