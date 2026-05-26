package com.richie.component.mqtt.client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * MQTT事件发布失败原因
 * <p>
 * 用于表示事件发布失败的具体原因，帮助调用方进行相应的处理。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-08-13
 */
@Getter
@RequiredArgsConstructor
public enum PublishFailureReason {

    /**
     * Sink已终止
     * <p>
     * 当所有订阅者都取消订阅后，Sink会进入终止状态，
     * 此时无法再发布新的事件。
     */
    SINK_TERMINATED("Sink已终止"),

    /**
     * 缓冲区溢出
     * <p>
     * 当事件缓冲区已满时，新的事件会被丢弃，
     * 这通常表示下游处理速度跟不上上游发布速度。
     */
    BUFFER_OVERFLOW("缓冲区溢出"),

    /**
     * 操作被取消
     * <p>
     * 发布操作被显式取消，通常是由于订阅者取消订阅或系统关闭。
     */
    OPERATION_CANCELLED("操作被取消"),

    /**
     * 非序列化访问
     <p>
     * 多个线程同时访问Sink，违反了序列化访问规则。
     * 这通常表示存在并发访问问题。
     */
    NON_SERIALIZED_ACCESS("非序列化访问"),

    /**
     * 异常发生
     * <p>
     * 在发布过程中发生了未预期的异常。
     */
    EXCEPTION("异常发生"),

    /**
     * 未知错误
     * <p>
     * 发生了未知的错误，无法确定具体原因。
     */
    UNKNOWN_ERROR("未知错误"),

    /**
     * 没有可用订阅
     * <p>
     * 没有可用的订阅者，无法发布事件。
     * 这通常表示订阅者已经取消订阅或订阅者不存在。
     */
    NO_AVAILABLE_SUBSCRIPTION("没有可用订阅");


    /**
     * 获取失败原因描述
     */
    private final String description;

    @Override
    public String toString() {
        return description;
    }
}
