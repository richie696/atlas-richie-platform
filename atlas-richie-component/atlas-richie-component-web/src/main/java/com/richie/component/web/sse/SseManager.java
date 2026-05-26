package com.richie.component.web.sse;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Server-sent events (SSE) 管理器
 *
 * <p>SSE (Server-Sent Events) 和 WebSocket 的区别
 * <p>通信方式：
 * <ul>
 *   <li>SSE：单向通信，服务器可以向客户端发送消息，但客户端不能向服务器发送消息。</li>
 *   <li>WebSocket：双向通信，客户端和服务器可以相互发送消息。</li>
 * </ul>
 * <p>协议：
 * <ul>
 *   <li>SSE：基于HTTP协议，使用text/event-stream MIME类型。</li>
 *   <li>WebSocket：独立的协议，使用ws://或wss://作为URL前缀。</li>
 * </ul>
 * <p>连接保持：
 * <ul>
 *   <li>SSE：保持HTTP连接，服务器可以持续发送事件。</li>
 *   <li>WebSocket：保持TCP连接，支持全双工通信。</li>
 * </ul>
 * <p>浏览器支持：
 * <ul>
 *   <li>SSE：大多数现代浏览器都支持，但不支持IE。</li>
 *   <li>WebSocket：大多数现代浏览器都支持，包括IE10及以上版本。</li>
 * </ul>
 * <p>使用场景：
 * <ul>
 *   <li>SSE：适用于服务器向客户端推送实时更新，如新闻、股票价格等。</li>
 *   <li>WebSocket：适用于需要双向实时通信的场景，如在线聊天、实时游戏等。</li>
 * </ul>
 * <p>实现复杂度：
 * <ul>
 *   <li>SSE：实现相对简单，基于HTTP协议，容易与现有的HTTP基础设施集成。</li>
 *   <li>WebSocket：实现较复杂，需要专门的服务器和客户端支持。</li>
 * </ul>
 * <p style="color:#ED4E4EFF">注意：SSE 适用于服务器向客户端推送实时更新的场景，但不适用于
 * 需要双向实时通信的场景。并且，SSE 与 WebSocket 之间并不是竞争关系，而是互补关系，可以根据
 * 具体的业务需求选择合适的通信方式。当SSE所在的服务需要多实例部署的时候，SseEmitter无法序列化
 * 仅能在单实例部署的情况下使用，如果需要多实例部署，需要使用Redis Stream/MQ等消息队列来广播当
 * 前实例收到的ClientID，然后由该服务的全部实例来消费该消息，由当前ClientID所在的服务实例去发送
 * 消息，其它不存在该的服务实例则直接不处理该消息即可，这样就可以实现多实例部署的情况下的SSE推送。
 *
 * <p><a href="https://codeup.aliyun.com/rydeen/platform/framework-backend-template/richie-component-frontend.git">⬇️前端对接代码下载</a>
 *
 * @author richie696
 * @version 1.0
 * @since 2024-08-12 16:05:23
 */
@Slf4j
public class SseManager {

    private static final ConcurrentMap<String, SseEmitter> EMITTER_MAP = new ConcurrentHashMap<>();
    @Getter
    public static SseManager instance = new SseManager();

    /**
     * 根据ClientID获取SseEmitter实例
     *
     * @param id clientId
     * @return 返回SseEmitter实例
     */
    public SseEmitter getEmitter(String id) {
        return EMITTER_MAP.get(id);
    }

    /**
     * 将SseEmitter实例存放到缓存中
     *
     * @param id      clientId
     * @param emitter SseEmitter实例
     */
    public void putEmitter(String id, SseEmitter emitter) {
        EMITTER_MAP.put(id, emitter);
    }

    /**
     * 从缓存中移除SseEmitter实例
     *
     * @param id clientId
     */
    public void removeEmitter(String id) {
        EMITTER_MAP.remove(id);
    }

    /**
     * 判断是否包含指定的SseEmitter实例
     *
     * @param id clientId
     * @return 返回是否包含SseEmitter实例
     */
    public boolean containsEmitter(String id) {
        return EMITTER_MAP.containsKey(id);
    }

    /**
     * 发送消息
     *
     * @param id      clientId
     * @param message 消息内容
     * @return 返回是否发送成功
     */
    public boolean send(String id, Object message) {
        SseEmitter emitter = EMITTER_MAP.get(id);
        if (emitter != null) {
            try {
                emitter.send(message);
                return true;
            } catch (Exception e) {
                log.error("Send message error, throwable: {}", e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * 创建SseEmitter实例
     *
     * @param id clientId
     * @return 返回SseEmitter实例
     * @throws IOException 建立连接失败时抛出该异常
     */
    public SseEmitter createEmitter(String id) throws IOException {
        if (containsEmitter(id)) {
            SseEmitter emitter = getEmitter(id);
            emitter.send(SseEmitter.event().reconnectTime(1000).data("服务器建立连接成功"));
            return emitter;
        }
        SseEmitter emitter = new SseEmitter(3600_000L);
        putEmitter(id, emitter);

        emitter.onCompletion(() -> {
            log.info("The client is connected.");
        });
        emitter.onTimeout(() -> {
            log.warn("The client is timeout.");
            removeEmitter(id);
        });
        emitter.onError(e -> {
            log.error("The client is error, throwable: {}", e.getMessage());
            removeEmitter(id);
        });
        emitter.send(SseEmitter.event().reconnectTime(1000).data("服务器建立连接成功"));
        return emitter;
    }

}
