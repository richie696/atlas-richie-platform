package com.richie.component.threadpool.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

@Data
public class TimeInfo implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDateTime time;

    @TableField(exist = false)
    private OffsetDateTime offsetDateTime;

    @TableField(exist = false)
    private ZonedDateTime zonedDateTime;
}
