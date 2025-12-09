package com.boda.canteen.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("recipe")
public class Recipe implements Serializable {

    private static final long serialVersionUID = 1L;

    // 核心：指定主键自增
    @TableId(type = IdType.AUTO)
    private Long recipeId;

    private String name;

    private String category;

    private String picture;

    private String unit;

    private Long price;

    private String description;
}