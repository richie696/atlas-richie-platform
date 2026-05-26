package com.richie.component.mongodb.listener;

import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatStartedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import com.mongodb.event.ServerMonitorListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 自定义MongoDB心跳监听器
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-03 23:55:01
 */
@Slf4j
@Component
@ConditionalOnMissingBean(ServerMonitorListener.class)
public class DefaultMongoServerMonitorListener implements ServerMonitorListener {

    /**
     * 心跳开始时回调。
     *
     * @param event 心跳开始事件
     */
    @Override
    public void serverHearbeatStarted(ServerHeartbeatStartedEvent event) {
        log.debug("MongoDB heartbeat started: {}", event.getConnectionId());
    }

    /**
     * 心跳成功时回调。
     *
     * @param event 心跳成功事件
     */
    @Override
    public void serverHeartbeatSucceeded(ServerHeartbeatSucceededEvent event) {
        log.debug("MongoDB heartbeat succeeded: {} in {}ms",
                event.getConnectionId(), event.getElapsedTime(TimeUnit.MILLISECONDS));
    }

    /**
     * 心跳失败时回调。
     *
     * @param event 心跳失败事件
     */
    @Override
    public void serverHeartbeatFailed(ServerHeartbeatFailedEvent event) {
        log.warn("MongoDB heartbeat failed: {} - {}",
                event.getConnectionId(), event.getThrowable().getMessage());
    }

}
