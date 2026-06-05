package com.richie.component.redis.streammq.stream;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis Stream 消费者注解
 *
 * <p>用于标记 Redis Stream 消费者类，指定配置名称
 * <p>配置名称对应 application.yml 中 platform.cache.redis.stream.consumers.configs 下的 key
 *
 * <p>使用示例：
 * <pre>{@code
 * &#64;RedisStreamConsumer("user-events")
 * public class UserInfoStreamConsumer extends AbstractStreamConsumer<UserInfo> {
 *     &#64;Override
 *     protected void handle(UserInfo userInfo, EventContext ctx) {
 *         // 处理逻辑
 *     }
 * }
 * }</pre>
 *
 * @author richie696
 * @since 2025-09-18
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface RedisStreamConsumer {

    /**
     * 配置名称，对应配置文件中的 key
     *
     * @return 配置名称
     */
    String value();
}
