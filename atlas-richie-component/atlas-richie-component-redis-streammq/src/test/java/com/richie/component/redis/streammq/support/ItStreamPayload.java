package com.richie.component.redis.streammq.support;

import com.richie.contract.model.BaseStreamMessage;

/**
 * 集测专用 Stream 消息体。
 */
public record ItStreamPayload(String message) implements BaseStreamMessage {
}
