/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.nats.strategy;

import cn.richie696.component.nats.exception.NatsSerializationException;
import cn.richie696.context.utils.data.JsonUtils;

/**
 * 基于 Jackson 的 NATS 消息序列化实现
 *
 * <p>委托 {@link JsonUtils} 完成序列化/反序列化，与项目其他组件保持一致的 JSON 处理方式。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class JacksonNatsMessageSerializer implements NatsMessageSerializer {

    /**
     * 将对象序列化为 UTF-8 字节数组。
     *
     * @param obj 待序列化对象，不可为 {@code null}
     * @return 序列化结果字节数组
     * @throws NatsSerializationException 当 {@code obj} 为 {@code null}、底层序列化结果为 {@code null}（例如目标类型本身为 {@code null}），
     *                                   或 Jackson 抛出的任何异常被统一包装时
     */
    @Override
    public byte[] serialize(Object obj) {
        try {
            byte[] bytes = JsonUtils.getInstance().serializeBytes(obj);
            // JsonUtils 在入参为 null 或目标值为 null 时可能返回 null，这里统一转为异常，
            // 避免下游误把“空负载”当作合法消息推到 broker。
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

    /**
     * 将字节数组反序列化为指定类型的实例。
     *
     * @param data 载荷字节数组
     * @param type 目标类型
     * @param <T>  目标泛型
     * @return 反序列化得到的实例
     * @throws NatsSerializationException 当反序列化结果为 {@code null}、载荷反序列化失败或类型不匹配时
     */
    @Override
    public <T> T deserialize(byte[] data, Class<T> type) {
        try {
            T result = JsonUtils.getInstance().deserializePayload(data, type);
            // 与 serialize 镜像处理：null 结果视为错误，避免把“空对象”当作成功响应继续下游编排。
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
