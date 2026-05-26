package com.richie.component.cache.redis.monitor;

import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.config.monitor.RedisStreamMonitoringProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis Stream 消息积压监控器
 *
 * <p>负责定期检查 Redis Stream 的消息积压情况，并更新监控指标
 *
 * <p>主要功能：
 * <ul>
 *   <li>定期检查各个 Stream 的消息积压数量</li>
 *   <li>更新 RedisStreamMetrics 中的积压指标</li>
 *   <li>提供积压情况的详细统计信息</li>
 *   <li>支持手动触发积压检查</li>
 * </ul>
 *
 * @author richie696
 * @version 5.0.0
 * @since 2025-09-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass({RedisStreamMetrics.class})
public class RedisStreamBacklogMonitor {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** Stream 指标 */
    private final RedisStreamMetrics metrics;

    /** 监控配置 */
    private final RedisStreamMonitoringProperties properties;

    /** 已监控的 Stream 键集合，避免重复扫描 */
    private final Set<String> monitoredStreams = ConcurrentHashMap.newKeySet();

    /** 各 Stream 的积压统计信息 */
    private final Map<String, BacklogInfo> backlogStats = new ConcurrentHashMap<>();

    /**
     * 积压信息记录
     *
     * @param streamKey      Stream 键
     * @param totalLength    总消息数
     * @param pendingCount   待确认数
     * @param consumerGroups 消费者组数
     * @param lastChecked    上次检查时间
     */
    public record BacklogInfo(String streamKey, long totalLength, long pendingCount,
                             long consumerGroups, Instant lastChecked) {
    }

    /**
     * 定期检查消息积压情况
     * 默认每30秒检查一次
     */
    @Scheduled(fixedRateString = "${platform.cache.redis.stream.monitoring.backlog.check-interval:30000}")
    public void checkMessageBacklog() {
        if (!properties.getMetrics().isEnabled()) {
            return;
        }

        try {
            long totalBacklog = 0;
            int checkedStreams = 0;

            // 检查已知的 Stream
            for (String streamKey : monitoredStreams) {
                try {
                    long backlog = calculateStreamBacklog(streamKey);
                    totalBacklog += backlog;
                    checkedStreams++;

                    // 更新统计信息
                    updateBacklogStats(streamKey, backlog);

                } catch (Exception e) {
                    log.warn("检查 Stream 积压失败: streamKey={}, error={}", streamKey, e.getMessage());
                }
            }

            // 更新总积压指标
            metrics.setMessageBacklog(totalBacklog);

            if (checkedStreams > 0) {
                log.debug("消息积压检查完成: 检查了{}个Stream, 总积压{}条消息", checkedStreams, totalBacklog);
            }

        } catch (Exception e) {
            log.error("消息积压检查异常", e);
        }
    }

    /**
     * 手动触发积压检查
     */
    public void refreshBacklog() {
        log.info("手动触发消息积压检查");
        checkMessageBacklog();
    }

    /**
     * 添加需要监控的 Stream
     *
     * @param streamKey Stream 键名
     */
    public void addMonitoredStream(String streamKey) {
        monitoredStreams.add(streamKey);
        log.debug("添加监控 Stream: {}", streamKey);
    }

    /**
     * 移除监控的 Stream
     *
     * @param streamKey Stream 键名
     */
    public void removeMonitoredStream(String streamKey) {
        monitoredStreams.remove(streamKey);
        backlogStats.remove(streamKey);
        log.debug("移除监控 Stream: {}", streamKey);
    }

    /**
     * 获取积压统计信息
     *
     * @return 各 Stream 的积压统计及汇总
     */
    public Map<String, Object> getBacklogStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalBacklog = 0;
        for (BacklogInfo info : backlogStats.values()) {
            totalBacklog += info.pendingCount();
        }

        stats.put("totalBacklog", totalBacklog);
        stats.put("monitoredStreams", monitoredStreams.size());
        stats.put("streamDetails", backlogStats);
        stats.put("lastChecked", Instant.now().toString());

        return stats;
    }

    /**
     * 计算指定 Stream 的积压数量
     *
     * @param streamKey Stream 键名
     * @return 积压数量（待确认消息数），异常时返回 0
     */
    private long calculateStreamBacklog(String streamKey) {
        try {
            // 获取 Stream 基本信息
            StreamInfo.XInfoStream streamInfo = redisTemplate.opsForStream().info(streamKey);

            // 获取消费者组信息
            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);
            if (groups.isEmpty()) {
                // 没有消费者组，积压就是总长度
                return streamInfo.streamLength();
            }

            // 计算所有消费者组的待处理消息数
            long totalPending = 0;
            for (StreamInfo.XInfoGroup group : groups) {
                totalPending += group.pendingCount();
            }

            return totalPending;

        } catch (Exception e) {
            log.debug("计算 Stream 积压失败: streamKey={}, error={}", streamKey, e.getMessage());
            return 0;
        }
    }

    /**
     * 更新积压统计信息
     *
     * @param streamKey Stream 键名
     * @param backlog   积压数量
     */
    private void updateBacklogStats(String streamKey, long backlog) {
        try {
            StreamInfo.XInfoStream streamInfo = redisTemplate.opsForStream().info(streamKey);
            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);

            long totalLength = streamInfo.streamLength();
            long consumerGroups = groups.size();

            BacklogInfo info = new BacklogInfo(
                streamKey,
                totalLength,
                backlog,
                consumerGroups,
                Instant.now()
            );

            backlogStats.put(streamKey, info);

        } catch (Exception e) {
            log.debug("更新积压统计失败: streamKey={}, error={}", streamKey, e.getMessage());
        }
    }

    /**
     * 获取指定 Stream 的积压信息
     *
     * @param streamKey Stream 键名
     * @return 积压信息，未监控时为 null
     */
    public BacklogInfo getStreamBacklog(String streamKey) {
        return backlogStats.get(streamKey);
    }

    /**
     * 清理过期的统计信息
     * 清理超过1小时未更新的统计信息
     */
    @Scheduled(fixedRate = 300000) // 每5分钟清理一次
    public void cleanupExpiredStats() {
        Instant cutoff = Instant.now().minusSeconds(3600); // 1小时前

        backlogStats.entrySet().removeIf(entry ->
            entry.getValue().lastChecked().isBefore(cutoff)
        );
    }
}
