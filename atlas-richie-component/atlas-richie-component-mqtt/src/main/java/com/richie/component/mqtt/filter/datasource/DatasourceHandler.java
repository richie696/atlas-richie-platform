package com.richie.component.mqtt.filter.datasource;

/**
 * 数据源处理器
 * <p>
 * 用于消息去重和缓存的数据源处理器接口。
 * <p>
 * <strong>设计说明：</strong>
 * <ul>
 *   <li>接口方法使用消息的 hash 值（String）作为参数，而不是完整的消息对象</li>
 *   <li>这样可以避免依赖具体的消息类型（如 ConsumerMessage），提高接口的通用性</li>
 *   <li>调用方需要在调用前计算消息 payload 的 hash 值（通常使用 SHA-256）</li>
 * </ul>
 *
 * @author richie696
 * @version 2.0
 * @since 2022-09-16 18:09:05
 */
public interface DatasourceHandler {

    /**
     * 缓存键前缀
     * <p>
     * 用于在Redis或内存中存储消息去重标识的前缀。
     */
    String KEY = "rydeen:platform:duplicate:mqtt:";

    /**
     * 检查当前消息是否是重复消息的方法
     *
     * @param hash 消息 payload 的 hash 值（通常使用 SHA-256 计算）
     * @return 返回检查结果（true：是重复消息，false：不是重复消息）
     */
    boolean isDuplicate(String hash);

    /**
     * 保存消息数据的方法
     *
     * @param hash 消息 payload 的 hash 值（通常使用 SHA-256 计算）
     * @param expired 该消息的过期时间（单位：毫秒）
     */
    void saveCache(String hash, long expired);

    /**
     * 获取缓存KEY的方法
     *
     * @param hash 消息 payload 的 hash 值（通常使用 SHA-256 计算）
     * @return 返回完整的缓存键（前缀 + hash）
     */
    default String getCacheKey(String hash) {
        return KEY + hash;
    }
}
