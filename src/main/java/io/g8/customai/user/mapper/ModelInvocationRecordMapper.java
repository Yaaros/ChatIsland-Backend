package io.g8.customai.user.mapper;

import io.g8.customai.user.DTO.ModelInvocationRecord;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

/**
 * 模型调用记录Mapper接口
 */
@Mapper
public interface ModelInvocationRecordMapper {

    /**
     * 插入模型调用记录
     */
    @Insert("INSERT INTO model_invocation_record (uid, content, model_name, invocation_time, tokens_used, success) " +
            "VALUES (#{uid}, #{content}, #{modelName}, #{invocationTime}, #{tokensUsed}, #{success})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ModelInvocationRecord record);

    /**
     * 获取用户当天的调用次数
     */
    @Select("SELECT COUNT(*) FROM model_invocation_record " +
            "WHERE uid = #{uid} AND DATE(invocation_time) = CURDATE() AND success = true")
    int countTodayInvocations(String uid);

    /**
     * 获取用户历史调用记录
     */
    @Select("SELECT * FROM model_invocation_record WHERE uid = #{uid} " +
            "ORDER BY invocation_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<ModelInvocationRecord> findByUid(@Param("uid") String uid,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    /**
     * 获取用户在指定日期范围内的调用记录
     */
    @Select("SELECT * FROM model_invocation_record WHERE uid = #{uid} " +
            "AND invocation_time BETWEEN #{startDate} AND #{endDate} " +
            "ORDER BY invocation_time DESC")
    List<ModelInvocationRecord> findByUidAndDateRange(
            @Param("uid") String uid,
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate);

    /**
     * 统计用户在指定日期指定模型的调用次数
     */
    @Select("SELECT COUNT(*) FROM model_invocation_record " +
            "WHERE uid = #{uid} AND model_name = #{modelName} " +
            "AND DATE(invocation_time) = #{date} AND success = true")
    int countModelInvocations(@Param("uid") String uid,
                              @Param("modelName") String modelName,
                              @Param("date") Date date);
}