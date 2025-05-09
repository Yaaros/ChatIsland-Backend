package io.g8.customai.user.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Getter
@Setter
@ToString
public class User {
    // 用户唯一标识
    private String uid;
    // 用户名
    private String name;
    // 用户类别枚举
    public enum Category {
        ADMIN, VIP, NORMAL, CS
    }
    // 用户类别
    private Category category;
    // 用户密码
    private String password;
    // 用户创建时间
    private Date createTime;

    // 无参构造函数
    public User() {
    }

    // 构造函数
    public User(String uid, String name, Category category, String password) {
        this.uid = uid;
        this.name = name;
        this.category = category;
        this.password = password;
        this.createTime = new Date(); // 自动设置创建时间为当前时间
    }
}