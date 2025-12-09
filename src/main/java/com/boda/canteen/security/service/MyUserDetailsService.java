package com.boda.canteen.security.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.boda.canteen.entity.MyUser;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class MyUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(MyUserDetailsService.class);

    @Autowired
    private MyUserService myUserService;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 查询数据库用户
        LambdaQueryWrapper<MyUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StrUtil.isNotEmpty(username), MyUser::getUsername, username);
        MyUser myUser = myUserService.getOne(queryWrapper);

        if (myUser == null) {
            log.error("用户名不存在：{}", username);
            throw new UsernameNotFoundException("用户名不存在");
        }

        // 构建用户权限
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + myUser.getRole()));

        // 关键修复：移除重复加密（数据库密码应已加密存储，直接使用即可）
        // 若数据库密码未加密，需先在注册/初始化时加密，而非登录时加密
        return new User(
                myUser.getUsername(),
                myUser.getPassword(), // 直接使用数据库密码（已加密）
                authorities
        );
    }
}