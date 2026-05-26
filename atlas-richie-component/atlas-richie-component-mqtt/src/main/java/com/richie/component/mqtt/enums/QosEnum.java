package com.richie.component.mqtt.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * QoS 值定义
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-13 11:14:32
 */
@Getter
@RequiredArgsConstructor
public enum QosEnum {
    /**
     * 最多一次
     */
    AT_MOST_ONCE(0),
    /**
     * 至少一次
     */
    AT_LEAST_ONCE(1),
    /**
     * 仅有一次
     */
    EXACTLY_ONCE(2);

    private final int value;

    /**
     * 根据QoS值获取对应的枚举
     *
     * @param qos QoS值（0、1或2）
     * @return 对应的QoS枚举
     * @throws IllegalArgumentException 如果QoS值无效
     */
    public static QosEnum qosOf(int qos) {
        return Arrays.stream(values()).filter(o -> o.value == qos).findFirst().orElseThrow(IllegalArgumentException::new);
    }

    /**
     * 检查QoS值是否有效
     *
     * @param qos QoS值
     * @return 如果QoS值在有效范围内（0-2）返回true，否则返回false
     */
    public static boolean isValid(int qos) {
        return qos >= AT_MOST_ONCE.value && qos <= EXACTLY_ONCE.value;
    }
}
