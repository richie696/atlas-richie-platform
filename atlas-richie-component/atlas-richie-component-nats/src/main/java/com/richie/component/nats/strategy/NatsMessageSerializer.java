package com.richie.component.nats.strategy;

/**
 * NATS 消息序列化策略接口
 *
 * <p>负责业务对象与 {@code byte[]} 之间的双向转换。默认实现基于 Jackson，
 * 用户可通过 Bean 替换自定义实现。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface NatsMessageSerializer {

    /**
     * 将对象序列化为字节数组
     *
     * @param obj 待序列化对象
     * @return 序列化后的字节数组
     */
    byte[] serialize(Object obj);

    /**
     * 将字节数组反序列化为指定类型
     *
     * @param data 字节数组
     * @param type 目标类型
     * @param <T>  目标泛型
     * @return 反序列化后的对象
     */
    <T> T deserialize(byte[] data, Class<T> type);
}
