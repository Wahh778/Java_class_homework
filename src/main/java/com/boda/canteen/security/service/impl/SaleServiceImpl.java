package com.boda.canteen.security.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.boda.canteen.entity.Sale;
import com.boda.canteen.mapper.SaleMapper;
import com.boda.canteen.security.service.SaleService;
import org.springframework.stereotype.Service;

@Service
public class SaleServiceImpl extends ServiceImpl<SaleMapper, Sale> implements SaleService {
}
