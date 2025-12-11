package com.boda.canteen.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("shopCart")
public class ShopCart implements Serializable {

    private static final long serialVersionUID = 1L;

    // 添加主键策略修改为自增
    @TableId(type = IdType.AUTO)
    private Integer scId;

    private String name;

    private String unit;

    private Integer weight;

    private Long price;

    private Long totalPrice;

    private String picture;

    private Long userId;
}