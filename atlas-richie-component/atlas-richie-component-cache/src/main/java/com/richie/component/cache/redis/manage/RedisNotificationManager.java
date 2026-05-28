package com.richie.component.cache.redis.manage;

import com.richie.component.cache.function.NotificationFunction;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 发布通知管理器，封装了Redis发布订阅（Pub/Sub）机制的通知功能。
 * <p>
 * <b>与Stream消息队列的区别：</b>
 * <ul>
 *   <li><b>发布订阅（convertAndSend）</b>：基于Redis的Pub/Sub机制，消息只在内存中，只有在线订阅者能收到，离线后消息丢失，无持久化、无消费确认，适合事件通知、在线推送等场景。</li>
 *   <li><b>Stream消息队列</b>：基于Redis Stream数据结构，消息持久化存储，支持消费组、消息确认、消息堆积和回溯，适合可靠消息、异步任务、日志收集等场景。</li>
 * </ul>
 * <b>本类只封装了发布订阅（Pub/Sub）机制，适用于在线通知、推送、事件广播等对可靠性要求不高的场景。</b>
 * <br/>
 * 若需可靠消息、消费确认、消息堆积等能力，请使用{@link RedisStreamManager}
 * <table style="border: 1px solid #ccc; border-collapse: collapse; width: 100%; margin-top: 10px;">
 *   <caption>Stream消息队列与发布订阅（Pub/Sub）对比</caption>
 *   <tr style="border: 1px solid #ccc;">
 *     <th>特性</th>
 *     <th>opsForStream (Stream)</th>
 *     <th>convertAndSend (Pub/Sub)</th>
 *   </tr>
 *   <tr style="border: 1px solid #ccc;">
 *     <td>持久化</td>
 *     <td>是</td>
 *     <td>否</td>
 *   </tr>
 *   <tr style="border: 1px solid #ccc;">
 *     <td>消费确认</td>
 *     <td>是（ACK/重试/回溯）</td>
 *     <td>否</td>
 *   </tr>
 *   <tr style="border: 1px solid #ccc;">
 *     <td>离线消息</td>
 *     <td>可回溯/堆积</td>
 *     <td>丢失</td>
 *   </tr>
 *   <tr style="border: 1px solid #ccc;">
 *     <td>消费模式</td>
 *     <td>队列/分组/可靠消费</td>
 *     <td>广播/推送</td>
 *   </tr>
 *   <tr style="border: 1px solid #ccc;">
 *     <td>典型场景</td>
 *     <td>任务队列、异步处理、日志</td>
 *     <td>通知、推送、在线事件</td>
 *   </tr>
 * </table>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-26 10:53:37
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisNotificationManager implements NotificationFunction {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    private final RedisPerfGuard redisPerfGuard;

    /**
     * 发布消息到指定频道的方法
     *
     * @param topic 发布消息的主题
     * @param message 消息内容
     * @return 返回接收到消息的订阅者数量（当Redis处于管道或事务环境中时，返回null）
     * @apiNote
     * <p><b>时间复杂度</b>：{@code O(1)}（PUBLISH）；消息体过大时网络与序列化主导延迟。
     * <p><b>严禁</b>：广播超大 payload 或超高频频道填满出口带宽。
     * <p><b>可用</b>：在线通知、轻量事件广播（与 Stream 可靠投递区分）。
     * <p><b>注意</b>：Pub/Sub 不持久化，离线订阅者收不到历史消息。
     */
    @Override
    public Long publishNotify(String topic, Object message) {
        return redisPerfGuard.<Long>execute("RedisNotificationManager", "publishNotify", RedisOperationCatalog.PUBLISH,
                () -> redisTemplate.convertAndSend(topic, message));
    }

}
