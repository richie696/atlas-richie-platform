package com.richie.component.cache.controller;

import com.richie.contract.model.ApiResult;
import com.richie.component.cache.GlobalCache;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/redis/queue")
public class RedisQueueController {

    public record MessageData(String topic, Object message) {
    }

    @PostMapping("/send")
    public ApiResult<?> send(@RequestBody MessageData data) {
        Long count = GlobalCache.publishNotification(data.topic, data.message);
        return ApiResult.success(count);
    }
}
