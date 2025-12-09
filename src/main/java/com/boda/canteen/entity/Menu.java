package com.boda.canteen.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("menu")
public class Menu implements Serializable {

    private static final long serialVersionUID = 1L;

    // 添加主键生成策略：自增（需数据库表配合设置自增）
    @TableId(type = IdType.AUTO)
    private Long menuId;

    private String name;

    private String category;

    private String picture;

    private String unit;

    private Long price;

    private Date createTime;
}