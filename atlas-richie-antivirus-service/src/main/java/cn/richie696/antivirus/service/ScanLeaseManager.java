package cn.richie696.antivirus.service;

import cn.richie696.antivirus.config.AntivirusProperties;
import cn.richie696.component.cache.GlobalCache;
import cn.richie696.context.utils.data.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis 扫描租约与恢复索引。
 *
 * <p>租约避免多个 Pod 同时执行同一任务；ZSet 使已 ACK 但执行实例宕机的任务仍可重新投递。</p>
 */
@Component
@RequiredArgsConstructor
public class ScanLeaseManager {
    private static final String ACQUIRE_SCRIPT = """
            if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then
                redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
                return 1
            end
            return 0
            """;
    private static final String COMPLETE_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('ZREM', KEYS[2], ARGV[2])
                return 1
            end
            return 0
            """;
    private static final String IS_OWNER_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return 1
            end
            return 0
            """;
    private static final String SCHEDULE_FAILURE_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('ZADD', KEYS[2], ARGV[1], ARGV[2])
                return 1
            end
            return 0
            """;
    private static final String REMOVE_EXPIRED_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return redis.call('ZREM', KEYS[2], ARGV[1])
            end
            return 0
            """;
    private static final String FIND_DUE_SCRIPT = """
            return redis.call(
                'ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1],
                'LIMIT', 0, ARGV[2]
            )
            """;

    private final AntivirusProperties properties;
    private final Clock clock = Clock.systemUTC();

    /**
     * 原子取得租约并登记恢复时间，杜绝 Pod 在两次写 Redis 之间退出造成永久 SCANNING。
     */
    public boolean tryAcquire(String taskId, String ownerToken) {
        long leaseMillis = properties.getRecovery().getLeaseDuration().toMillis();
        long expiresAt = clock.millis() + leaseMillis;
        String recoveryMember = serializeMember(taskId);
        Long acquired = GlobalCache.script().eval(
                ACQUIRE_SCRIPT,
                List.of(leaseKey(taskId), properties.getRecovery().getScheduleKey()),
                List.of(ownerToken, Long.toString(leaseMillis), Long.toString(expiresAt), recoveryMember),
                Long.class);
        return Long.valueOf(1L).equals(acquired);
    }

    public boolean isOwner(String taskId, String ownerToken) {
        Long owner = GlobalCache.script().eval(
                IS_OWNER_SCRIPT,
                leaseKey(taskId),
                List.of(ownerToken),
                Long.class);
        return Long.valueOf(1L).equals(owner);
    }

    /** 仅租约所有者可以释放租约和恢复记录，防止旧执行者误删新执行者的租约。 */
    public void complete(String taskId, String ownerToken) {
        GlobalCache.script().eval(
                COMPLETE_SCRIPT,
                List.of(leaseKey(taskId), properties.getRecovery().getScheduleKey()),
                List.of(ownerToken, serializeMember(taskId)),
                Long.class);
    }

    /**
     * 消费在取得租约前失败时登记重试；已有租约则保留领取时写入的原恢复时间。
     */
    public void scheduleAfterFailure(String taskId) {
        long retryAt = clock.millis() + properties.getRecovery().getRetryDelay().toMillis();
        GlobalCache.script().eval(
                SCHEDULE_FAILURE_SCRIPT,
                List.of(leaseKey(taskId), properties.getRecovery().getScheduleKey()),
                List.of(Long.toString(retryAt), serializeMember(taskId)),
                Long.class);
    }

    /**
     * 发布恢复消息后，仅在没有新租约时删除旧标记；与领取脚本互斥，避免删掉新租约的恢复时间。
     */
    public void removeExpiredMarkerIfNoLease(String taskId) {
        GlobalCache.script().eval(
                REMOVE_EXPIRED_SCRIPT,
                List.of(leaseKey(taskId), properties.getRecovery().getScheduleKey()),
                List.of(serializeMember(taskId)),
                Long.class);
    }

    public void discardRecovery(String taskId) {
        GlobalCache.ranking().remove(properties.getRecovery().getScheduleKey(), taskId);
    }

    public Set<String> findDueTaskIds() {
        int batchSize = Math.max(1, properties.getRecovery().getBatchSize());
        @SuppressWarnings("unchecked")
        List<Object> members = GlobalCache.script().eval(
                FIND_DUE_SCRIPT,
                properties.getRecovery().getScheduleKey(),
                List.of(Long.toString(clock.millis()), Integer.toString(batchSize)),
                List.class);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }

        Set<String> taskIds = new LinkedHashSet<>(members.size());
        for (Object member : members) {
            String json = member instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8)
                    : String.valueOf(member);
            taskIds.add(json.startsWith("\"")
                    ? JsonUtils.getInstance().deserialize(json, String.class)
                    : json);
        }
        return taskIds;
    }

    private String leaseKey(String taskId) {
        return properties.getRecovery().getLeaseKeyPrefix() + taskId;
    }

    /**
     * ZSet 使用 jsonTemplate；Lua 必须写入同一种 JSON 字节表示，才能被 RankingOps 正确读取和删除。
     */
    private String serializeMember(String taskId) {
        return JsonUtils.getInstance().serialize(taskId);
    }
}
