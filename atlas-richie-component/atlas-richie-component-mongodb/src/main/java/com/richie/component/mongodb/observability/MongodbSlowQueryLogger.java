package com.richie.component.mongodb.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MongoDB 慢查询日志记录器
 *
 * <p>当 MongoDB 操作耗时超过阈值（200ms）时记录警告日志</p>
 */
@Component
@Slf4j
public class MongodbSlowQueryLogger {

    private static final long THRESHOLD_MS = 200L;

    public void logIfSlow(String collection, String operation, long durationMs) {
        if (durationMs > THRESHOLD_MS) {
            log.warn("Slow MongoDB operation: collection={} op={} duration={}ms",
                    collection, operation, durationMs);
        }
    }
}
