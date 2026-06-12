package com.richie.component.http.core;

import java.time.Duration;

/**
 * SSE（Server-Sent Events）事件。
 * <p>
 * 对应 SSE 协议中以空行分隔的一个完整事件，由 {@code id} / {@code event} /
 * {@code data} / {@code retry} 四种字段组合而成。各字段均可为 {@code null}，
 * 由具体的协议解析器根据实际帧内容填充。
 *
 * <h2>字段含义</h2>
 * <ul>
 *   <li>{@code id}：事件 ID，对应协议中的 {@code id:} 行；客户端会在断线重连时回传。</li>
 *   <li>{@code event}：事件类型，对应协议中的 {@code event:} 行；默认 {@code message}。</li>
 *   <li>{@code data}：事件数据，对应协议中的 {@code data:} 行；多行 data 以 {@code \n} 拼接。</li>
 *   <li>{@code retry}：服务端建议的重连间隔（毫秒），对应 {@code retry:} 行；为 {@code null} 表示未指定。</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public record SseEvent(String id, String event, String data, Duration retry) {

    /**
     * SSE 协议默认值：当服务端未显式声明事件类型时使用 {@code message}。
     */
    public static final String DEFAULT_EVENT_NAME = "message";

    /**
     * 创建仅包含 {@code data} 的事件，{@code id}/{@code retry} 默认为 {@code null}，
     * {@code event} 默认为 {@code message}。
     *
     * @param data 事件数据，可为 {@code null}
     * @return 新的 {@link SseEvent} 实例
     */
    public static SseEvent of(String data) {
        return new SseEvent(null, DEFAULT_EVENT_NAME, data, null);
    }

}