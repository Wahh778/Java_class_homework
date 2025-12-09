package com.boda.canteen;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        // 创建BCrypt加密器（和项目中用的是同一个）
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        // 生成123456对应的加密串（你可以替换成自己的密码）
        String pwd = encoder.encode("123456");
        System.out.println("加密后的密码：" + pwd);
    }
}