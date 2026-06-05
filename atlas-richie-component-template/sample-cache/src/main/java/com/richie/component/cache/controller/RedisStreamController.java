package com.richie.component.cache.controller;

import com.richie.component.redis.streammq.StreamMQ;
import com.richie.component.cache.domain.UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Redis Stream 消息生产者控制器
 *
 * <p>提供 Redis Stream 消息发布功能，支持：
 * <ul>
 *   <li>发布用户信息消息</li>
 *   <li>批量发布消息</li>
 *   <li>获取 Stream 状态</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-01-15
 */
@Slf4j
@RestController
@RequestMapping("/api/redis/stream")
@RequiredArgsConstructor
public class RedisStreamController {


    // Stream 配置
    private static final String STREAM_KEY = "user-events";
    private static final String CONSUMER_GROUP = "user-processors";

    /**
     * 发布用户信息消息
     *
     * @param userInfo 用户信息
     * @return 发布结果
     */
    @PostMapping("/publish/user")
    public Map<String, Object> publishUserInfo(@RequestBody UserInfo userInfo) {
        try {
            log.info("准备发布用户信息消息: {}", userInfo);

            // 添加时间戳
            userInfo.setSlogan("%s - 发布时间: %s".formatted(userInfo.getSlogan(), Instant.now()));

            // 发布消息到 Redis Stream
            String messageId = StreamMQ.stream().publish(STREAM_KEY, userInfo);

            log.info("用户信息消息发布成功: messageId={}, userInfo={}", messageId, userInfo);

            return Map.of(
                    "success", true,
                    "messageId", messageId,
                    "streamKey", STREAM_KEY,
                    "userInfo", userInfo,
                    "timestamp", Instant.now().toString()
            );

        } catch (Exception e) {
            log.error("发布用户信息消息失败", e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "timestamp", Instant.now().toString()
            );
        }
    }

    /**
     * 获取 Stream 状态
     *
     * @return Stream 状态信息
     */
    @GetMapping("/status")
    public Map<String, Object> getStreamStatus() {
        try {
            log.info("获取 Stream 状态: streamKey={}", STREAM_KEY);

            // 这里可以调用 Redis Stream 管理端点获取状态
            // 或者直接调用 GlobalCache 相关方法

            return Map.of(
                    "streamKey", STREAM_KEY,
                    "consumerGroup", CONSUMER_GROUP,
                    "status", "active",
                    "timestamp", Instant.now().toString(),
                    "note", "请通过 /actuator/redisstream 端点获取详细状态"
            );

        } catch (Exception e) {
            log.error("获取 Stream 状态失败", e);
            return Map.of(
                    "error", e.getMessage(),
                    "timestamp", Instant.now().toString()
            );
        }
    }

}
