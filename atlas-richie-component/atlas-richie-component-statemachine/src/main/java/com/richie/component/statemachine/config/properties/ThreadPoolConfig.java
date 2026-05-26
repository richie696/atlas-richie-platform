package com.richie.component.statemachine.config.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步数据库持久化配置
 * <p>
 * 配置通过异步线程池实现数据库持久化的行为，包括批量大小、刷新间隔等。
 * 仅在 storageType=ASYNC_THREAD 时生效。
 *
 * @author richie696
 * @version 1.0
 * @since 5.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.statemachine.thread-pool")
public class ThreadPoolConfig {

    /**
     * 批量大小
     * <p>
     * 批量写入数据库的记录数，达到此数量时触发批量写入。
     *
     */
    private int batchSize = 200;

    /**
     * 最大缓冲条数（触发紧急刷写）
     * <p>
     * 当缓冲区达到此大小时，立即触发刷写，避免缓冲区溢出。
     *
     */
    private int bufferCapacity = 10000;

    /**
     * 定时刷新间隔（毫秒）
     * <p>
     * 定期检查缓冲区，如果有数据则触发刷写，确保数据及时持久化。
     * 即使未达到批量大小，也会定期刷写缓冲区中的数据。
     *
     */
    private long flushIntervalMs = 2000;

}
