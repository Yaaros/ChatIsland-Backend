package io.g8.customai.user.mapper;
import io.g8.customai.user.DTO.VipChangeRecord;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

/**
 * VIP变动记录Mapper接口
 */
@Mapper
public interface VipChangeRecordMapper {

    /**
     * 插入VIP变动记录
     */
    @Insert("INSERT INTO vip_change_record (uid, change_type, start_time, end_time, reason, active) " +
            "VALUES (#{uid}, #{changeType}, #{startTime}, #{endTime}, #{reason}, #{active})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(VipChangeRecord record);

    /**
     * 更新VIP记录状态
     */
    @Update("UPDATE vip_change_record SET active = #{active}, reason = #{reason} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("active") boolean active, @Param("reason") String reason);

    /**
     * 获取用户最新的活跃VIP记录
     */
    @Select("SELECT * FROM vip_change_record WHERE uid = #{uid} AND active = true " +
            "ORDER BY start_time DESC LIMIT 1")
    VipChangeRecord findLatestActiveByUid(String uid);

    /**
     * 获取所有将在指定日期前过期的活跃VIP记录
     */
    @Select("SELECT * FROM vip_change_record WHERE active = true AND end_time < #{date}")
    List<VipChangeRecord> findExpiredRecords(Date date);

    /**
     * 获取用户历史VIP变动记录
     */
    @Select("SELECT * FROM vip_change_record WHERE uid = #{uid} ORDER BY start_time DESC")
    List<VipChangeRecord> findHistoryByUid(String uid);

    /**
     * 设置用户所有活跃VIP记录为非活跃
     */
    @Update("UPDATE vip_change_record SET active = false WHERE uid = #{uid} AND active = true")
    int deactivateAllByUid(String uid);
}