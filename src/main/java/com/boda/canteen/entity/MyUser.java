package com.boda.canteen.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("myuser") // 改为全小写，匹配数据库表名（建议数据库表名统一小写）
public class MyUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId // 无需指定value，关闭驼峰后直接映射数据库的userId字段
    private Long userId;

    private String name;
    private String username;
    private String password;
    private String sex;
    private String telephone;
    private String department;
    private String role;
}