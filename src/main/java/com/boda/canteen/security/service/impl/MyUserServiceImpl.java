package com.boda.canteen.security.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.boda.canteen.entity.MyUser;
import com.boda.canteen.mapper.MyUserMapper;
import com.boda.canteen.security.service.MyUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MyUserServiceImpl extends ServiceImpl<MyUserMapper, MyUser> implements MyUserService {
}
