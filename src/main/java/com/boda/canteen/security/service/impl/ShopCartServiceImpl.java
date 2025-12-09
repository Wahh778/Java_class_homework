package com.boda.canteen.security.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.boda.canteen.entity.ShopCart;
import com.boda.canteen.mapper.ShopCartMapper;
import com.boda.canteen.security.service.ShopCartService;
import org.springframework.stereotype.Service;

@Service
public class ShopCartServiceImpl extends ServiceImpl<ShopCartMapper, ShopCart> implements ShopCartService {
}