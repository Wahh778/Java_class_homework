package com.boda.canteen.security.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.boda.canteen.entity.Recipe;
import com.boda.canteen.mapper.RecipeMapper;
import com.boda.canteen.security.service.RecipeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecipeServiceImpl extends ServiceImpl<RecipeMapper, Recipe> implements RecipeService {
}