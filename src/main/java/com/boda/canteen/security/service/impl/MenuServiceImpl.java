package com.boda.canteen.security.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.boda.canteen.entity.Menu;
import com.boda.canteen.mapper.MenuMapper;
import com.boda.canteen.security.service.MenuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {
}
