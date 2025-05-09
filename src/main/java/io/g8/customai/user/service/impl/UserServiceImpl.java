package io.g8.customai.user.service.impl;

import io.g8.customai.user.entity.User;
import io.g8.customai.user.mapper.UserMapper;
import io.g8.customai.user.service.UserService;
import io.g8.customai.common.security.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public User register(User user) {
        // 检查用户名是否已存在
        if (userMapper.findByName(user.getName()) != null) {
            return null; // 用户名已存在
        }

        // 生成唯一ID
        if (user.getUid() == null || user.getUid().isEmpty()) {
            user.setUid(UUID.randomUUID().toString());
        }

        // 设置创建时间
        user.setCreateTime(new Date());

        // 密码加密
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 默认用户类别
        if (user.getCategory() == null) {
            user.setCategory(User.Category.NORMAL);
        }

        // 保存用户
        userMapper.insert(user);

        return user;
    }

    @Override
    public String login(String name, String password) {
        // 查找用户
        User user = userMapper.findByName(name);
        if (user == null) {
            return null; // 用户不存在
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return null; // 密码错误
        }

        // 生成JWT令牌
        return jwtUtil.generateToken(user.getUid(), user.getName(), user.getCategory().toString());
    }

    @Override
    public User findByUid(String uid) {
        return userMapper.findByUid(uid);
    }

    @Override
    public User findByName(String name) {
        return userMapper.findByName(name);
    }

    @Override
    @Transactional
    public boolean updateUser(User user) {
        // 获取原用户信息
        User oldUser = userMapper.findByUid(user.getUid());
        if (oldUser == null) {
            return false; // 用户不存在
        }

        // 如果修改了密码，需要加密
        if (user.getPassword() != null && !user.getPassword().equals(oldUser.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        // 更新用户
        return userMapper.update(user) > 0;
    }

    @Override
    @Transactional
    public boolean deleteUser(String uid) {
        return userMapper.delete(uid) > 0;
    }
}