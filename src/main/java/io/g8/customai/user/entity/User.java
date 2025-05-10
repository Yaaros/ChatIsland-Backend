package io.g8.customai.user.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Getter
@Setter
@ToString
public class User {
    private String uid;
    private String name;
    public enum Category {
        ADMIN, VIP, NORMAL, CS
    }
    private Category category;
    private String password;
    private Date createTime;
    public User() {
    }
    public User(String uid, String name, Category category, String password) {
        this.uid = uid;
        this.name = name;
        this.category = category;
        this.password = password;
        this.createTime = new Date(); // 自动设置创建时间为当前时间
    }
}