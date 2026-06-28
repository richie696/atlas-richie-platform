package com.richie.component.nats.strategy;

import com.richie.component.nats.exception.NatsSerializationException;
import com.richie.component.nats.strategy.NatsMessageSerializer;
import com.richie.context.utils.data.JsonUtils;

/**
 * 基于 Jackson 的 NATS 消息序列化实现
 *
 * <p>委托 {@link JsonUtils} 完成序列化/反序列化，与项目其他组件保持一致的 JSON 处理方式。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class JacksonNatsMessageSerializer implements NatsMessageSerializer {

    @Override
    public byte[] serialize(Object obj) {
        try {
            byte[] bytes = JsonUtils.getInstance().serializeBytes(obj);
            if (bytes == null) {
                throw new NatsSerializationException("Serialized result is null for object: " + obj.getClass().getName());
            }
            return bytes;
        } catch (NatsSerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new NatsSerializationException("Failed to serialize object: " + obj.getClass().getName(), e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> type) {
        try {
            T result = JsonUtils.getInstance().deserializePayload(data, type);
            if (result == null) {
                throw new NatsSerializationException("Deserialized result is null for type: " + type.getName());
            }
            return result;
        } catch (NatsSerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new NatsSerializationException("Failed to deserialize to type: " + type.getName(), e);
        }
    }
}
