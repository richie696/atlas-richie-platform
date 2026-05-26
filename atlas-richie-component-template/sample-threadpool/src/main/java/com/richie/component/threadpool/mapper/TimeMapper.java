package com.richie.component.threadpool.mapper;

import com.richie.component.threadpool.domain.TimeInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TimeMapper extends BaseMapper<TimeInfo> {
}
