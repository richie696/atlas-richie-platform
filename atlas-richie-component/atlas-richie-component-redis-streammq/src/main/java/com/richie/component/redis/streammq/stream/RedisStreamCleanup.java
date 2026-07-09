/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.redis.streammq.stream;

import com.richie.component.cache.GlobalCache;
import com.richie.component.redis.streammq.config.stream.RedisStreamProperties;
import com.richie.component.cache.redis.manage.CacheLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Redis Stream 消息清理服务
 * <p>
 * 使用分布式锁确保多实例环境下只有一个实例执行清理任务，避免重复操作。
 * 
 * <p>
 * <strong>工作原理：</strong>
 * 
 * <ol>
 *   <li>定时任务（默认每小时执行一次）在 JOB 开始时获取服务级别的分布式锁</li>
 *   <li>锁的粒度是按服务隔离的，不同服务之间互不影响</li>
 *   <li>只有获取到锁的实例执行所有 Stream 的清理操作</li>
 *   <li>其他实例跳过本次清理，避免重复操作</li>
 * </ol>
 * <p>
 * <strong>锁的设计：</strong>
 * 
 * <ul>
 *   <li>锁的 key 格式：{@code redis:stream:cleanup:lock:{serviceName}}</li>
 *   <li>每个服务有自己独立的锁，不同服务之间互不影响</li>
 *   <li>同一服务的多个实例之间互斥，确保只有一个实例执行清理</li>
 *   <li>在 JOB 开始时获取锁，整个清理过程完成后释放</li>
 * </ul>
 *
 * @author richie696
 * @since 2025/11/05
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "platform.cache.redis.stream.consumers",
    name = "enabled",
    havingValue = "true"
)
public class RedisStreamCleanup {

    /** Stream 相关配置 */
    private final RedisStreamProperties streamProperties;

    /**
     * 服务名称（用于生成服务级别的分布式锁）
     * <p>
     * 从 {@code spring.application.name} 配置中获取，用于区分不同服务。
     * 不同服务有各自独立的清理锁，互不影响。
     * 
     */
    @Value("${spring.application.name:default-service}")
    private String serviceName;

    /**
     * 分布式锁键前缀
     */
    private static final String CLEANUP_LOCK_KEY_PREFIX = "redis:stream:cleanup:lock:";

    /**
     * 锁超时时间（秒）
     * <p>
     * 设置为 5 分钟，确保清理任务有足够时间完成。
     * 如果清理任务执行时间超过此值，锁会自动释放，其他实例可以获取锁。
     * 
     */
    private static final long LOCK_TIMEOUT_SECONDS = 300L; // 5分钟

    /**
     * 定时任务调度器（使用独立的 ScheduledExecutorService，避免与 Spring 的 TaskScheduler 冲突）
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "redis-stream-cleanup-scheduler");
        t.setDaemon(true);
        return t;
    });

    /**
     * 定时任务 Future，用于取消任务
     */
    private ScheduledFuture<?> scheduledTask;

    /**
     * 初始化定时任务
     * <p>
     * 从配置中读取 Duration 格式的 interval，动态注册定时任务。
     * 
     */
    @PostConstruct
    public void initScheduledTask() {
        Duration interval = streamProperties.getCleanup().getInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            log.warn("Stream 清理服务已启用，但清理间隔配置无效，使用默认值 1 小时");
            interval = Duration.ofHours(1);
        }

        long intervalMillis = interval.toMillis();

        // 使用固定频率调度任务
        scheduledTask = scheduler.scheduleAtFixedRate(
            this::cleanupStreams,
            intervalMillis,  // 初始延迟
            intervalMillis,   // 执行间隔
            TimeUnit.MILLISECONDS
        );

        log.info("Stream 清理定时任务已启动，清理间隔: {}", interval);
    }

    /**
     * 优雅停机：停止定时任务和调度器
     */
    @PreDestroy
    public void shutdown() {
        try {
            log.info("开始停止 Stream 清理定时任务");

            // 取消定时任务
            if (scheduledTask != null && !scheduledTask.isDone()) {
                scheduledTask.cancel(false);
                log.debug("定时任务已取消");
            }

            // 关闭调度器
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    log.warn("调度器未能及时停止，强制关闭");
                    scheduler.shutdownNow();
                } else {
                    log.debug("调度器已正常关闭");
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
                log.warn("调度器关闭被中断，已强制关闭");
            }

            log.info("Stream 清理定时任务已停止");
        } catch (Exception e) {
            log.error("停止 Stream 清理定时任务异常", e);
        }
    }

    /**
     * 定时清理任务
     * <p>
     * 在 JOB 开始时获取服务级别的分布式锁，确保同一服务的多个实例中只有一个实例执行清理。
     * 不同服务之间互不影响，每个服务有自己独立的锁。
     * 
     * <p>
     * 清理策略：
     * 
     * <ul>
     *   <li>有独立 maxLen 配置的 Stream：单独清理（使用单键接口）</li>
     *   <li>使用全局 defaultMaxLen 的 Stream：批量清理（使用多键接口，提高效率）</li>
     *   <li>直接执行 XTRIM，无需提前检查长度（如果长度未超过限制，XTRIM 返回 0，开销很小）</li>
     * </ul>
     */
    public void cleanupStreams() {
        // 在 JOB 开始时获取服务级别的分布式锁
        // 锁的 key 格式：redis:stream:cleanup:lock:{serviceName}
        // 不同服务有各自独立的锁，互不影响
        String serviceLockKey = CLEANUP_LOCK_KEY_PREFIX + serviceName;

        try (CacheLock lock = GlobalCache.lock().optimistic(serviceLockKey, LOCK_TIMEOUT_SECONDS)) {
            if (!lock.isSuccess()) {
                // 其他实例正在处理，跳过本次执行
                if (log.isTraceEnabled()) {
                    log.trace("Stream 清理服务锁获取失败，其他实例正在处理: serviceName={}", serviceName);
                }
                return;
            }

            // 获取锁成功，执行清理任务
            log.debug("获取 Stream 清理服务锁成功，开始执行清理任务: serviceName={}", serviceName);

            Map<String, RedisStreamProperties.ConsumerConfig> configs = streamProperties.getConfigs();
            if (configs == null || configs.isEmpty()) {
                log.debug("没有配置的 Stream 消费者，跳过清理");
                return;
            }

            // 获取全局默认 maxLen
            Long globalDefaultMaxLen = streamProperties.getCleanup().getDefaultMaxLen();
            if (globalDefaultMaxLen == null || globalDefaultMaxLen <= 0) {
                globalDefaultMaxLen = 0L;
            }

            // 分类：有独立配置的和使用全局默认值的
            List<StreamCleanupItem> individualItems = new ArrayList<>();  // 有独立 maxLen 配置的
            List<String> batchStreamKeys = new ArrayList<>();              // 使用全局默认值的 Stream keys

            for (Map.Entry<String, RedisStreamProperties.ConsumerConfig> entry : configs.entrySet()) {
                String configName = entry.getKey();
                RedisStreamProperties.ConsumerConfig config = entry.getValue();

                String streamKey = config.getStreamKey();
                if (streamKey == null || streamKey.isEmpty()) {
                    continue;
                }

                Long maxLen = config.getMaxLen();
                if (maxLen != null && maxLen > 0) {
                    // 有独立配置，单独清理
                    individualItems.add(new StreamCleanupItem(streamKey, maxLen, configName));
                } else if (globalDefaultMaxLen > 0) {
                    // 使用全局默认值，加入批量清理列表
                    batchStreamKeys.add(streamKey);
                }
            }

            int cleanedCount = 0;
            int skippedCount = 0;

            // 1. 单独清理有独立 maxLen 配置的 Stream
            for (StreamCleanupItem item : individualItems) {
                try {
                    boolean cleaned = trimStream(item.streamKey, item.maxLen, item.configName);
                    if (cleaned) {
                        cleanedCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    log.error("Stream 清理失败: streamKey={}, configName={}", item.streamKey, item.configName, e);
                    skippedCount++;
                }
            }

            // 2. 批量清理使用全局默认值的 Stream
            if (!batchStreamKeys.isEmpty()) {
                try {
                    int batchCleaned = trimStreamsBatch(batchStreamKeys, globalDefaultMaxLen);
                    cleanedCount += batchCleaned;
                    skippedCount += (batchStreamKeys.size() - batchCleaned);
                } catch (Exception e) {
                    log.error("批量 Stream 清理失败: streamKeys={}, defaultMaxLen={}", batchStreamKeys, globalDefaultMaxLen, e);
                    skippedCount += batchStreamKeys.size();
                }
            }

            if (cleanedCount > 0 || skippedCount > 0) {
                log.debug("Stream 清理任务完成: serviceName={}, 单独清理 {} 个, 批量清理 {} 个, 成功 {} 个, 跳过 {} 个",
                    serviceName, individualItems.size(), batchStreamKeys.size(), cleanedCount, skippedCount);
            } else {
                log.debug("Stream 清理任务完成（无需清理）: serviceName={}", serviceName);
            }

        } catch (Exception e) {
            log.error("Stream 清理任务执行异常: serviceName={}", serviceName, e);
        }
    }

    /**
     * Stream 清理项（用于单独清理）
     *
     * @param streamKey  Stream 键名
     * @param maxLen     最大保留消息数
     * @param configName 配置项名称
     */
    private record StreamCleanupItem(String streamKey, Long maxLen, String configName) {
    }

    /**
     * 执行单个 Stream 清理（XTRIM）
     * <p>
     * 使用 XTRIM 命令的近似修剪模式（MAXLEN ~ count），提高性能。
     * 
     * <p>
     * <strong>性能优化说明：</strong>
     * 
     * <ul>
     *   <li>使用 {@code ~} 参数进行近似修剪，避免阻塞 Redis 服务器</li>
     *   <li>修剪后的 Stream 长度可能稍微超过 maxLen（但不会少于 maxLen）</li>
     *   <li>这是性能和精确度的权衡，适合大多数生产场景</li>
     *   <li>近似修剪可以移除完整的宏节点，减少内存碎片，提高效率</li>
     *   <li>直接执行 XTRIM，无需提前检查长度：如果长度未超过限制，XTRIM 会返回 0，开销很小</li>
     * </ul>
     *
     * @param streamKey Stream 键名
     * @param maxLen 最大保留消息数
     * @param configName 配置名称（用于日志）
     * @return true 表示执行了清理操作，false 表示未执行（长度未超过限制或 Stream 不存在）
     */
    private boolean trimStream(String streamKey, long maxLen, String configName) {
        try {
            // 检查 Stream 是否存在
            if (!GlobalCache.key().hasKey(streamKey)) {
                log.debug("Stream 不存在，跳过清理: streamKey={}", streamKey);
                return false;
            }

            // 直接执行 XTRIM 命令，保留最新的 maxLen 条消息
            // 使用 ~ 参数进行近似修剪，提高性能，避免阻塞 Redis 服务器
            // XTRIM key MAXLEN ~ count - 近似修剪（高效，推荐）
            // 注意：
            // 1. 使用 ~ 参数后，Stream 长度可能稍微超过 maxLen（但不会少于 maxLen）
            // 2. 如果 Stream 长度 <= maxLen，XTRIM 会返回 0，不会报错，开销很小
            String trimScript = "return redis.call('XTRIM', KEYS[1], 'MAXLEN', '~', ARGV[1])";
            Long trimmed = GlobalCache.script().eval(trimScript, streamKey, List.of(String.valueOf(maxLen)), Long.class);

            if (trimmed != null && trimmed > 0) {
                log.debug("Stream 清理完成: streamKey={}, configName={}, 清理消息数={}",
                    streamKey, configName, trimmed);
                return true;
            } else {
                // trimmed == 0 或 null，表示无需清理（长度未超过限制或 Stream 为空）
                log.debug("Stream 无需清理: streamKey={}, configName={}, maxLen={}",
                    streamKey, configName, maxLen);
                return false;
            }

        } catch (Exception e) {
            log.error("执行 Stream 清理失败: streamKey={}, configName={}, maxLen={}",
                streamKey, configName, maxLen, e);
            throw e;
        }
    }

    /**
     * 批量执行 Stream 清理（XTRIM）
     * <p>
     * 使用批量 Lua 脚本对多个 Stream 执行 XTRIM 命令，提高效率。
     * 
     * <p>
     * <strong>批量清理优势：</strong>
     * 
     * <ul>
     *   <li>减少网络往返：一次调用处理多个 Stream</li>
     *   <li>提高吞吐量：批量操作比逐个操作更高效</li>
     *   <li>原子性：Lua 脚本保证批量操作的原子性</li>
     * </ul>
     *
     * @param streamKeys Stream 键名列表
     * @param maxLen 最大保留消息数（全局默认值）
     * @return 成功清理的 Stream 数量
     */
    private int trimStreamsBatch(List<String> streamKeys, long maxLen) {
        if (streamKeys == null || streamKeys.isEmpty()) {
            return 0;
        }

        try {
            // 批量清理 Lua 脚本
            // 遍历所有 keys，对每个 key 执行 XTRIM，返回清理结果统计
            // 返回格式：{cleanedCount, totalTrimmed}
            String batchTrimScript = "local cleanedCount = 0 " +
                                     "local totalTrimmed = 0 " +
                                     "for i = 1, #KEYS do " +
                                     "  local key = KEYS[i] " +
                                     "  if redis.call('EXISTS', key) == 1 then " +
                                     "    local trimmed = redis.call('XTRIM', key, 'MAXLEN', '~', ARGV[1]) " +
                                     "    if trimmed and trimmed > 0 then " +
                                     "      cleanedCount = cleanedCount + 1 " +
                                     "      totalTrimmed = totalTrimmed + trimmed " +
                                     "    end " +
                                     "  end " +
                                     "end " +
                                     "return {cleanedCount, totalTrimmed}";

            // 执行批量清理
            // Lua 脚本返回 {cleanedCount, totalTrimmed}，会被转换为 List
            @SuppressWarnings("unchecked")
            List<Object> result = GlobalCache.script().eval(batchTrimScript, streamKeys, List.of(String.valueOf(maxLen)), List.class);

            if (result != null && !result.isEmpty()) {
                Object cleanedCountObj = result.get(0);
                Object totalTrimmedObj = result.size() >= 2 ? result.get(1) : 0L;

                int cleanedCount = cleanedCountObj instanceof Number
                    ? ((Number) cleanedCountObj).intValue()
                    : 0;
                long totalTrimmed = totalTrimmedObj instanceof Number
                    ? ((Number) totalTrimmedObj).longValue()
                    : 0L;

                if (cleanedCount > 0) {
                    log.debug("批量 Stream 清理完成: 清理 {} 个 Stream, 总清理消息数={}, defaultMaxLen={}",
                        cleanedCount, totalTrimmed, maxLen);
                } else {
                    log.debug("批量 Stream 清理完成（无需清理）: streamKeys={}, defaultMaxLen={}",
                        streamKeys, maxLen);
                }

                return cleanedCount;
            } else {
                log.warn("批量 Stream 清理返回结果异常: streamKeys={}, result={}", streamKeys, result);
                return 0;
            }

        } catch (Exception e) {
            log.error("批量 Stream 清理失败: streamKeys={}, defaultMaxLen={}", streamKeys, maxLen, e);
            return 0;
        }
    }
}


