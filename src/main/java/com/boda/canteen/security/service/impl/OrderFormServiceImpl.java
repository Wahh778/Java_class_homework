package com.boda.canteen.security.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.boda.canteen.entity.OrderForm;
import com.boda.canteen.mapper.OrderFormMapper;
import com.boda.canteen.security.service.OrderFormService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderFormServiceImpl extends ServiceImpl<OrderFormMapper, OrderForm> implements OrderFormService {
}

