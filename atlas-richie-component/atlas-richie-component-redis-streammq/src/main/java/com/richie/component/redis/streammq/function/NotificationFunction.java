package com.richie.component.redis.streammq.function;

import com.richie.component.cache.function.CacheFunction;

public interface NotificationFunction extends CacheFunction {
    Long publishNotify(String topic, Object message);
}
