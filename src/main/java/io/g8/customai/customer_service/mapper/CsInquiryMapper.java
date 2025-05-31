package io.g8.customai.customer_service.mapper;

import io.g8.customai.customer_service.entity.CsInquiry;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CsInquiryMapper {

    @Insert("INSERT INTO cs_inquiry (user_uid, status, message_content, assigned_cs_uid, inquiry_time, reply_time) " +
            "VALUES (#{userUid}, #{status}, #{messageContent}, #{assignedCsUid}, #{inquiryTime}, #{replyTime})")
    void insert(CsInquiry inquiry);

    @Select("SELECT * FROM cs_inquiry WHERE user_uid = #{userUid}")
    List<CsInquiry> findByUserUid(@Param("userUid") String userUid);

    @Select("SELECT * FROM cs_inquiry WHERE assigned_cs_uid = #{csUid}")
    List<CsInquiry> findByAssignedCsUid(@Param("csUid") String csUid);
}
