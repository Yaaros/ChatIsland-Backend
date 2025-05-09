package io.g8.customai.user.service;

import io.g8.customai.user.entity.User;

public interface UserService {

    /**
     * 用户注册
     * @param user 用户信息
     * @return 注册成功返回用户信息，失败返回null
     */
    User register(User user);

    /**
     * 用户登录
     * @param name 用户名
     * @param password 密码
     * @return 登录成功返回JWT令牌，失败返回null
     */
    String login(String name, String password);

    /**
     * 通过用户ID查找用户
     * @param uid 用户ID
     * @return 用户对象
     */
    User findByUid(String uid);

    /**
     * 通过用户名查找用户
     * @param name 用户名
     * @return 用户对象
     */
    User findByName(String name);

    /**
     * 更新用户信息
     * @param user 用户信息
     * @return 成功返回true，失败返回false
     */
    boolean updateUser(User user);

    /**
     * 删除用户
     * @param uid 用户ID
     * @return 成功返回true，失败返回false
     */
    boolean deleteUser(String uid);
}