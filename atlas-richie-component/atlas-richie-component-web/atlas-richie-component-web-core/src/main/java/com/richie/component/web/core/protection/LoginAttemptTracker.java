package com.richie.component.web.core.protection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败计数器（README.md §4.8.2 BruteForce）。
 * <p>
 * 滑动窗口内失败次数超阈值则锁定。线程安全（{@link ConcurrentHashMap} + per-key {@link ArrayDeque}）。
 *
 * <h2>状态机</h2>
 * <pre>
 *   attempts &lt; maxAttempts        → 计数 + 放行
 *   attempts == maxAttempts       → 触发锁定（lockUntil = now + lockoutSeconds）
 *   attempts &gt; maxAttempts 在窗口内 → 持续刷新窗口最早一项（淘汰）
 *   lockUntil &gt; now               → locked
 *   lockUntil &lt;= now              → 解锁，重置窗口
 * </pre>
 *
 * @author richie696
 * @since 2026-07
 */
public class LoginAttemptTracker {

    private final long windowMillis;
    private final int maxAttempts;
    private final long lockoutMillis;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public LoginAttemptTracker(long windowSeconds, int maxAttempts, long lockoutSeconds) {
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be > 0");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        if (lockoutSeconds <= 0) {
            throw new IllegalArgumentException("lockoutSeconds must be > 0");
        }
        this.windowMillis = windowSeconds * 1000L;
        this.maxAttempts = maxAttempts;
        this.lockoutMillis = lockoutSeconds * 1000L;
    }

    /**
     * 检查 key 当前是否被锁定（未到时则拒绝）。
     */
    public boolean isLocked(String key) {
        if (key == null) {
            return false;
        }
        Entry e = entries.get(key);
        if (e == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (e.lockedUntil > now) {
            return true;
        }
        // 已解锁，清除过期 attempts
        if (e.attempts.isEmpty()) {
            entries.remove(key, e);
        } else {
            evictOlderThan(e, now - windowMillis);
        }
        return false;
    }

    /**
     * 记录一次失败；返回当前 attempts 计数。
     */
    public int recordFailure(String key) {
        if (key == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        Entry e = entries.computeIfAbsent(key, k -> new Entry());
        synchronized (e) {
            evictOlderThan(e, now - windowMillis);
            e.attempts.addLast(now);
            if (e.attempts.size() >= maxAttempts) {
                e.lockedUntil = now + lockoutMillis;
                return e.attempts.size();
            }
            return e.attempts.size();
        }
    }

    /**
     * 记录一次成功（清空窗口）。登录成功后调用。
     */
    public void recordSuccess(String key) {
        if (key == null) {
            return;
        }
        entries.remove(key);
    }

    public int size() {
        return entries.size();
    }

    private static void evictOlderThan(Entry e, long cutoff) {
        Long head;
        while ((head = e.attempts.peekFirst()) != null && head < cutoff) {
            e.attempts.pollFirst();
        }
    }

    private static final class Entry {
        final Deque<Long> attempts = new ArrayDeque<>();
        volatile long lockedUntil;
    }
}