package com.richie.component.cache.function;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * List类型缓存管理器接口，定义了所有对 Redis List 类型的通用操作能力。
 * <p>适用于队列、消息列表、批量数据等场景。
 */
public interface ListFunction extends CacheFunction {
    /**
     * 防缓存击穿：List
     *
     * @param key   资源键
     * @param index 获取值的队列索引
     * @param clazz 目标缓存类型
     * @param dbLoader 回源加载器
     * @param timeout 超时时间
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值
     */
    <T> List<T> getFromListWithLock(String key, long index, Class<T> clazz, Supplier<List<T>> dbLoader, long timeout);

    /**
     * 根据资源键获取资源值的方法
     *
     * @param key   资源键
     * @param index 获取值的队列索引（-1：为获取全部）
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值
     */
    <T> List<T> getFromList(String key, long index, Class<T> clazz);

    /**
     * 批量添加元素到指定列表的方法（本方法将会添加重复值）
     *
     * @param key    列表名称
     * @param values 列表值
     */
    void addAndReplaceList(String key, List<?> values);

    /**
     * 批量添加缓存到Redis List的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     */
    void batchAddToList(Map<String, List<?>> map);

    /**
     * 添加元素到指定列表的方法（本方法将会添加重复值）
     *
     * @param key   列表名称
     * @param value 列表值
     */
    void addListItem(String key, Object value);

    /**
     * 添加元素到指定列表的方法（本方法将会添加重复值）
     *
     * @param key   列表名称
     * @param index 要更新的列表索引值
     * @param value 列表值
     */
    void updateListItem(String key, long index, Object value);

    /**
     * 删除列表内指定项的方法
     *
     * @param key   列表名称
     * @param value 列表值
     * @param count 删除的数量
     */
    void removeListItem(String key, Object value, int count);

    /**
     * 从列表队首弹出元素的方法
     *
     * @param key   元素KEY
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回执行结果
     */
    <T> T leftPopListElement(String key, Class<T> clazz);

    /**
     * 从列表队首弹出元素的方法
     *
     * @param key   元素KEY
     * @param count 弹出元素的数量
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回执行结果
     */
    <T> List<T> leftPopListElement(String key, long count, Class<T> clazz);

    /**
     * 从列表队尾弹出元素的方法
     *
     * @param key   元素KEY
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回执行结果
     */
    <T> T rightPopListElement(String key, Class<T> clazz);

    /**
     * 从列表队首弹出元素的方法
     *
     * @param key   元素KEY
     * @param count 弹出元素的数量
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回执行结果
     */
    <T> List<T> rightPopListElement(String key, long count, Class<T> clazz);

    /**
     * 从列表队首压入元素的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @return 返回执行结果
     */
    Long leftPushListElement(String key, Object value);

    /**
     * 获取List集合元素数量的方法
     *
     * @param key 元素KEY
     * @return 返回执行结果
     */
    Long getListSize(String key);

    /**
     * 从列表队尾压入元素的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @return 返回执行结果
     */
    Long rightPushListElement(String key, Object value);
}
