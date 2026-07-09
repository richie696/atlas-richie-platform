/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.ops;

import com.richie.component.cache.enums.KeyTypeEnum;
import org.springframework.dao.DataAccessException;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Date;
import java.util.Set;

/**
 * Key管理与元数据操作接口，定义了所有对 Redis Key 及其元数据的通用操作能力。
 * <p>适用于Key生命周期管理、类型判断、批量操作等场景。
 */
public interface KeyOps {

    /**
     * 获取指定KEY过期时间
     *
     * @param key 需要获取过期时间的key
     * @return 过期时间（单位：毫秒）
     */
    Long getExpire(String key);

    /**
     * 获取指定节点下的所有KEY
     *
     * @param key 需要获取的某组KEY的父节点
     * @return 该父节点下的所有子节点KEY
     */
    Set<String> getAllKeys(String key);

    /**
     * 设置对应缓存过期时间
     *
     * @param key     缓存键
     * @param timeout 超时时间
     */
    void setExpiredTime(String key, long timeout);

    /**
     * 检查指定的Key是否存在
     *
     * @param key 缓存键
     * @return 检查结果
     */
    boolean hasKey(String key);

    /**
     * 根据Key删除指定元素
     * <p>（元素包含全部的Redis元素，比如：string, list, set, zset, hash, stream）
     *
     * @param key 列表名称
     */
    void removeCache(String key);

    /**
     * 根据Key列表删除指定元素
     * <p>（元素包含全部的Redis元素，比如：string, list, set, zset, hash, stream）
     *
     * @param keys Key列表
     */
    void removeCache(Collection<String> keys);

    /**
     * 合并两个数据集
     *
     * @param sourceKey 源数据集
     * @param targetKey 目标数据集
     * @param replace   是否替换目标数据集
     * @return 合并结果
     */
    boolean copy(String sourceKey, String targetKey, boolean replace);

    /**
     * 移动指定的Key到指定的数据库
     *
     * @param key     需要移动的KEY
     * @param dbIndex 目标数据库索引
     * @return 移动结果
     */
    boolean move(String key, int dbIndex);

    /**
     * 仅当目标 KEY 不存在时才将指定 KEY 重命名为目标 KEY
     *
     * @param oldKey 旧 KEY
     * @param newKey 新 KEY
     * @return 是否重命名结果（true：重命名，false：未重命名）
     * @throws DataAccessException 当访问的 oldKey 不存在时抛出此异常
     */
    boolean renameIfAbsent(String oldKey, String newKey) throws DataAccessException;

    /**
     * 重命名 KEY
     *
     * @param oldKey 旧 KEY
     * @param newKey 新 KEY
     * @throws DataAccessException 当访问的 oldKey 不存在时抛出此异常
     */
    void rename(String oldKey, String newKey) throws DataAccessException;

    /**
     * 序列化 KEY
     *
     * @param key 待序列化的 KEY
     * @return 序列化后的 KEY
     * @throws DataAccessException 当访问的 key 不存在时抛出此异常
     */
    byte[] dump(String key) throws DataAccessException;

    /**
     * 移除指定 key 的过期时间（执行后 KEY 将不再过期）
     *
     * @param key 待移除过期时间的 KEY
     * @return 移除结果
     */
    boolean persist(String key);

    /**
     * 指定时间点设置过期时间
     *
     * @param key  待设置过期时间的 KEY
     * @param date 过期的时间点
     * @return 设置结果
     */
    boolean expireAt(String key, final Date date);

    /**
     * 获取匹配的 KEY 数量
     *
     * @param keys KEY 集合
     * @return 匹配的 KEY 的个数
     */
    Long countExistingKeys(Collection<String> keys);

    /**
     * 获取指定Key的类型
     *
     * @param key 需要获取类型的Key
     * @return Key的类型枚举（KeyTypeEnum），如果Key不存在或类型不支持则返回null
     */
    @Nullable
    KeyTypeEnum getKeyType(String key);
}
