package com.boda.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("myuser") // 改为全小写，匹配数据库表名（建议数据库表名统一小写）
public class MyUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long userId;

    private String name;
    private String username;
    private String password;
    private String sex;
    private String telephone;
    private String department;
    private String role;
    private String work_information;
}