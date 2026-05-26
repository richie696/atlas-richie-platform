package com.richie.component.threadpool.service.impl;

import com.richie.component.threadpool.domain.TimeInfo;
import com.richie.component.threadpool.mapper.TimeMapper;
import com.richie.component.threadpool.service.TimeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TimeServiceImpl extends ServiceImpl<TimeMapper, TimeInfo> implements TimeService {
}
