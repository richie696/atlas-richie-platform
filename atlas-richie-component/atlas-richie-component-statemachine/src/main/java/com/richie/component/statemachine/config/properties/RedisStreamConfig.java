package com.richie.component.statemachine.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 存储配置
 * <p>
 * 仅在 storageType=REDIS 时生效，配置 Redis 相关的存储行为。
 * 
 * <p>
 * 注意：此类使用 Lombok {@code @Data} 注解，构造函数由 Lombok 自动生成。
 * 
 */
@Data
@ConfigurationProperties(prefix = "platform.component.statemachine.redis-stream")
public class RedisStreamConfig {
    /**
     * Redis缓存键前缀
     * 用于归类和管理状态机相关的Redis key，方便查看和维护
     * 默认值：platform:statemachine
     * 最终生成的key格式：{keyPrefix}:state:{stateMachineName}:{businessId}
     * 示例：platform:statemachine:state:order:123456
     */
    private String keyPrefix = "platform:statemachine";

    /**
     * 状态数据过期时间（单位：毫秒）
     * 默认7天，设置为0表示永不过期
     * 仅对历史记录生效，当前状态永不过期
     * 注意：历史记录建议设置合理的过期时间，避免Redis数据量过大
     */
    private long timeout = 7L * 24 * 60 * 60 * 1000; // 默认7天

    /**
     * Redis Stream 数据库持久化配置
     * <p>
     * 通过 Redis Stream 实现异步批量写入数据库（需要 Redis 5.0+）。
     * 
     */
    private RedisStreamDbReplicationConfig dbReplication = new RedisStreamDbReplicationConfig();

    /**
     * 终态自动清理配置
     * <p>
     * 配置定时任务自动清理 Redis 中长期处于终态/错误态的当前状态 Key，避免 Redis 数据无限增长。
     * 
     */
    private StorageCleanupConfig cleanup = new StorageCleanupConfig();

    /**
     * Redis Stream 数据库持久化配置
     * <p>
     * 配置通过 Redis Stream 实现数据库异步持久化的行为，包括批量大小等。
     * 仅在 storageType=REDIS 时生效。
     * 
     * <p>
     * 注意：此类使用 Lombok {@code @Data} 注解，构造函数由 Lombok 自动生成。
     * 
     */
    @Data
    @ConfigurationProperties(prefix = "platform.component.statemachine.redis-stream.db-replication")
    public static class RedisStreamDbReplicationConfig {
        /**
         * 是否开启数据库持久化
         * <p>
         * 设置为 true 时，状态变更会通过 Redis Stream 异步写入数据库。
         *
         */
        private boolean enabled = false;

        /**
         * 批量大小
         * <p>
         * 批量写入数据库的记录数，达到此数量时触发批量写入。
         *
         */
        private int batchSize = 200;
    }


    /**
     * 存储自动清理配置
     * <p>
     * 配置定时任务自动清理 Redis 中长期处于终态/错误态的当前状态 Key。
     * 仅在数据库复制（storage.db.enabled=true）开启时生效。
     * 
     */
    @Data
    @ConfigurationProperties(prefix = "platform.component.statemachine.redis-stream.cleanup")
    public static class StorageCleanupConfig {
        /**
         * 是否启用终态自动清理
         * <p>
         * 设置为 true 时，会启动定时任务，根据 ttlDays 和 batchSize 自动删除 Redis 当前状态 Key。
         * 
         */
        private boolean enabled = false;

        /**
         * 清理任务间隔时间（毫秒）
         * <p>
         * 用于表达配置含义：终态清理任务建议多久执行一次。<br>
         * 实际调度仍由 {@code platform.component.statemachine.storage.cleanup.fixed-delay-ms}
         * 这个配置键控制，本字段仅用于类型安全和文档说明，默认 3600000 毫秒（1 小时）。
         * 
         */
        private long cleanIntervalMs = 3600000L;

        /**
         * 终态保留天数
         * <p>
         * 当记录的 updated_at 早于当前时间减去该天数，且当前状态为 FINAL/ERROR 时，
         * 会被认为是“可清理”的终态状态。
         * 默认 30 天。
         * 
         */
        private long ttlDays = 30L;

        /**
         * 单次清理批次大小
         * <p>
         * 每次定时任务执行时，最多处理多少条候选记录，避免长时间占用数据库和 Redis。
         * 默认 500。
         * 
         */
        private int batchSize = 500;
    }
}
