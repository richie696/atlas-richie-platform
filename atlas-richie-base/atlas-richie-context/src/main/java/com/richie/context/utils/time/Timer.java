package com.richie.context.utils.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 计时器类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-03 16:11:24
 */
public class Timer {

    private Instant startTime;
    private Instant endTime;

    private Timer(boolean delay) {
        if (!delay) {
            begin();
        }
    }

    /**
     * 开始计时
     */
    public void begin() {
        startTime = Instant.now();
        endTime = Instant.now();
    }

    /**
     * 开始计时
     *
     * @param delay 是否延迟开始计时
     * @return 计时器对象
     */
    public static Timer start(boolean... delay) {
        return new Timer(delay != null && delay.length > 0 && delay[0]);
    }

    /**
     * 获取开始时间（服务器时间）
     *
     * @return 返回开始时间
     */
    public ZonedDateTime getStartTime() {
        return startTime.atZone(ZoneId.systemDefault());
    }

    /**
     * 获取结束时间（服务器时间）
     *
     * @return 返回结束时间
     */
    public ZonedDateTime getEndTime() {
        return endTime.atZone(ZoneId.systemDefault());
    }

    /**
     * 获取开始时间（自定义时区）
     *
     * @param zoneId 时区ID
     * @return 返回开始时间
     */
    public ZonedDateTime getStartTime(ZoneId zoneId) {
        return startTime.atZone(zoneId);
    }

    /**
     * 获取结束时间（自定义时区）
     *
     * @param zoneId 时区ID
     * @return 返回结束时间
     */
    public ZonedDateTime getEndTime(ZoneId zoneId) {
        return endTime.atZone(zoneId);
    }

    /**
     * 结束计时
     *
     * @return 返回计时时长（毫秒）
     */
    public long end() {
        endTime = Instant.now();
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }

    /**
     * 获取耗时
     *
     * @return 耗时（毫秒）
     */
    public long getDuration() {
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }

    /**
     * 重置计时器
     */
    public void reset() {
        begin();
    }
}
