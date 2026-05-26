package com.richie.component.threadpool.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class BaseInfo implements Serializable {

    @TableField(exist = false)
    private Integer currentPage;

    @TableField(exist = false)
    private Integer pageSize;

    @TableField(exist = false)
    private List<OrderItem> orders;

}
