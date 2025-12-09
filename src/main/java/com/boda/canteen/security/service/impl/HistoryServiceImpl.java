package com.boda.canteen.security.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.boda.canteen.entity.History;
import com.boda.canteen.mapper.HistoryMapper;
import com.boda.canteen.security.service.HistoryService;
import org.springframework.stereotype.Service;

@Service
public class HistoryServiceImpl extends ServiceImpl<HistoryMapper, History> implements HistoryService {
}
