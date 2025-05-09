package io.g8.customai.user.mapper;

import io.g8.customai.user.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    /**
     * 插入用户
     *
     * @param user 用户对象
     * @return 影响的行数
     */
    @Insert("INSERT INTO user (uid, name, category, password, create_time) " +
            "VALUES (#{uid}, #{name}, #{category}, #{password}, #{createTime})")
    int insert(User user);

    /**
     * 根据ID查询用户
     *
     * @param uid 用户ID
     * @return 用户对象
     */
    @Select("SELECT * FROM user WHERE uid = #{uid}")
    @Results({
            @Result(property = "uid", column = "uid"),
            @Result(property = "name", column = "name"),
            @Result(property = "category", column = "category"),
            @Result(property = "password", column = "password"),
            @Result(property = "createTime", column = "create_time")
    })
    User findByUid(String uid);

    /**
     * 根据用户名查询用户
     *
     * @param name 用户名
     * @return 用户对象
     */
    @Select("SELECT * FROM user WHERE name = #{name}")
    @Results({
            @Result(property = "uid", column = "uid"),
            @Result(property = "name", column = "name"),
            @Result(property = "category", column = "category"),
            @Result(property = "password", column = "password"),
            @Result(property = "createTime", column = "create_time")
    })
    User findByName(String name);

    /**
     * 更新用户信息
     *
     * @param user 用户对象
     * @return 影响的行数
     */
    @Update("<script>" +
            "UPDATE user " +
            "<set>" +
            "<if test='name != null'>name = #{name},</if>" +
            "<if test='category != null'>category = #{category},</if>" +
            "<if test='password != null'>password = #{password},</if>" +
            "</set>" +
            "WHERE uid = #{uid}" +
            "</script>")
    int update(User user);

    /**
     * 删除用户
     *
     * @param uid 用户ID
     * @return 影响的行数
     */
    @Delete("DELETE FROM user WHERE uid = #{uid}")
    int delete(String uid);
}