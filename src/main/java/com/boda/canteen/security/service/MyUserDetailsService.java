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
//Spring Security 登录时会调用该类的 loadUserByUsername 方法，从数据库加载用户信息并完成身份校验。
/*
对接数据库：根据前端传入的用户名，查询数据库中的用户信息。
封装权限：将数据库中的用户角色（如 manager/chef）转换为 Spring Security 认可的权限对象。
返回用户详情：封装成 UserDetails 对象，供 Spring Security 进行密码校验和权限判断。
 */
@Component// 标记为 Spring 组件，让容器扫描并注入
public class MyUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(MyUserDetailsService.class);

    @Autowired
    private MyUserService myUserService;// 自定义的用户业务层，用于查询数据库

    @Resource
    private PasswordEncoder passwordEncoder;// 密码加密器（此处未直接使用，仅注入备用）

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1.查询数据库用户，构建查询条件：根据用户名查询（StrUtil.isNotEmpty 防止空指针）
        LambdaQueryWrapper<MyUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StrUtil.isNotEmpty(username), MyUser::getUsername, username);
        MyUser myUser = myUserService.getOne(queryWrapper);

        // 2. 用户名不存在：抛出 Spring Security 定义的异常，会被登录失败处理器捕获
        if (myUser == null) {
            log.error("用户名不存在：{}", username);
            throw new UsernameNotFoundException("用户名不存在");
        }

        // 构建用户：将用户角色（如 manager、chef）转换为 Spring Security 认可的 GrantedAuthority（前缀 ROLE_ 是约定）。
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + myUser.getRole()));
        
        // 若数据库密码未加密，需先在注册/初始化时加密，而非登录时加密
        return new User(
                myUser.getUsername(),   // 数据库中的用户名
                myUser.getPassword(),   // 数据库中的加密密码（必须是 BCrypt 密文）
                authorities // 用户的权限/角色集合
        );
    }
}