package com.richie.component.cache.redis.manage;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.function.CacheFunction;
import com.richie.component.cache.ops.CacheInfrastructure;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DataAccessException;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Key管理与元数据操作子管理器
 *
 * @author richie696
 * @version 1.0.0
 * @since 2024-06-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisKeyManager implements KeyOps, CacheInfrastructure {

    /** 多数据源 Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** Redis 连接与行为配置 */
    private final AtlasRedisProperties redisProperties;

    /** Redis 性能守卫（可选启用） */
    private final RedisPerfGuard redisPerfGuard;

    /** 连接信息字符串（懒加载） */
    private String connectionString;

    /** key 到值类型的注册表，用于反序列化 */
    private static final Map<String, Class<?>> TYPE_REGISTRY = new ConcurrentHashMap<>();

    /**
     * 获取连接信息的方法
     *
     * @return 返回连接信息
     */
    @Override
    public String getConnectionString() {
        if (connectionString == null) {
            StringBuilder builder = new StringBuilder("\n=============== 【Redis 连接信息 开始】 ===============\n").append("HOST: ");
            if (Objects.nonNull(redisProperties.getSentinel())) {
                builder.append(redisProperties.getSentinel().getNodes()).append("\n");
            } else if (Objects.nonNull(redisProperties.getCluster())) {
                builder.append(redisProperties.getCluster().getNodes()).append("\n");
            } else if (StringUtils.isNotBlank(redisProperties.getUrl())) {
                builder.append(redisProperties.getUrl()).append("\n");
            } else {
                builder
                        .append(redisProperties.getHost()).append("\n")
                        .append("PORT: ").append(redisProperties.getPort()).append("\n");
            }
            builder
                    .append("SSL: ").append(redisProperties.getSsl().isEnabled()).append("\n")
                    .append("DATABASE: ").append(redisProperties.getDatabase()).append("\n")
                    .append(("=============== 【Redis 连接信息 结束】 ===============\n\n"));
            connectionString = builder.toString();
        }
        return connectionString;
    }

    /**
     * 检查是否启用二级缓存的方法
     * @return 返回是否启用二级缓存（true：启用，false：不启用）
     */
    @Override
    public boolean enableL2Caching() {
        return redisProperties.getEnableL2Caching();
    }

    /**
     * 检查指定数据类型是否开启二级缓存的方法
     *
     * @param keyType 二级缓存数据类型
     * @return 返回是否启用二级缓存（true：启用，false：不启用）
     */
    @Override
    public boolean enableKeyTypeCache(KeyTypeEnum keyType) {
        return redisProperties.getL2CachingData().contains(keyType);
    }

    @Override
    public Class<?> getValueType(String key) {
        return TYPE_REGISTRY.get(key);
    }

    @Override
    public void registerType(String key, Class<?> clazz) {
        if (TYPE_REGISTRY.containsKey(key)) {
            return;
        }
        TYPE_REGISTRY.put(key, clazz);
    }

    /**
     * 获取指定KEY过期时间的方法
     *
     * @param key 需要获取过期时间的key
     * @return 返回过期时间（单位：秒）
     */
    @Override
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }

    /**
     * 获取指定节点下的所有KEY的方法
     *
     * @param key 需要获取的某组KEY的父节点
     * @return 返回该父节点下的所有子节点KEY
     */
    @Override
    public Set<String> getAllKeys(String key) {
        return redisPerfGuard.<Set<String>>execute("RedisKeyManager", "getAllKeys", RedisOperationCatalog.KEYS_PATTERN, () -> {
            try {
                return redisTemplate.keys("%s*".formatted(key));
            } catch (Exception e) {
                log.warn("无法获取到缓存值（key = %s)".formatted(key));
                log.warn(getConnectionString());
                return Set.of();
            }
        });
    }

    /**
     * 设置对应缓存过期时间的方法
     *
     * @param key     缓存键
     * @param timeout 超时时间
     */
    @Override
    public void setExpiredTime(String key, long timeout) {
        if (!redisTemplate.hasKey(key)) {
            return;
        }
        long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
        redisTemplate.expire(key, realTimeout, TimeUnit.MILLISECONDS);
    }

    /**
     * 检查指定的Key是否存在的方法
     *
     * @param key 缓存键
     * @return 返回检查结果
     */
    @Override
    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }


    /**
     * 根据Key删除指定元素的方法
     * <p>（元素包含全部的Redis元素，比如：string, list, set, zset, hash, stream）
     *
     * @param key 列表名称
     */
    @Override
    public void removeCache(String key) {
        redisTemplate.unlink(key);
    }

    /**
     * 根据Key列表删除指定元素的方法
     * <p>（元素包含全部的Redis元素，比如：string, list, set, zset, hash, stream）
     *
     * @param keys Key列表
     */
    @Override
    public void removeCache(Collection<String> keys) {
        redisTemplate.unlink(keys);
    }

    /**
     * 合并两个数据集的方法
     *
     * @param sourceKey 源数据集
     * @param targetKey 目标数据集
     * @param replace   是否替换目标数据集
     * @return 返回合并结果
     */
    @Override
    public boolean copy(String sourceKey, String targetKey, boolean replace) {
        return redisTemplate.copy(sourceKey, targetKey, replace);
    }

    /**
     * 移动指定的Key到指定的数据库的方法
     *
     * @param key     需要移动的KEY
     * @param dbIndex 目标数据库索引
     * @return 返回移动结果
     */
    @Override
    public boolean move(String key, int dbIndex) {
        return redisTemplate.move(key, dbIndex);
    }

    /**
     * 仅当目标 KEY 不存在时才将指定 KEY 重命名为目标 KEY 的方法
     *
     * @param oldKey 旧 KEY
     * @param newKey 新 KEY
     * @return 返回是否重命名结果（true：重命名，false：未重命名）
     * @throws DataAccessException 当访问的 oldKey 不存在时抛出此异常
     */
    @Override
    public boolean renameIfAbsent(String oldKey, String newKey) throws DataAccessException {
        return redisTemplate.renameIfAbsent(oldKey, newKey);
    }

    /**
     * 重命名 KEY 的方法
     *
     * @param oldKey 旧 KEY
     * @param newKey 新 KEY
     * @throws DataAccessException 当访问的 oldKey 不存在时抛出此异常
     */
    @Override
    public void rename(String oldKey, String newKey) throws DataAccessException {
        redisTemplate.rename(oldKey, newKey);
    }

    /**
     * 序列化 KEY 的方法
     *
     * @param key 待序列化的 KEY
     * @return 返回序列化后的 KEY
     * @throws DataAccessException 当访问的 key 不存在时抛出此异常
     */
    @Override
    public byte[] dump(String key) throws DataAccessException {
        return redisTemplate.dump(key);
    }

    /**
     * 移除指定 key 的过期时间（执行后 KEY 将不再过期）
     *
     * @param key 待移除过期时间的 KEY
     * @return 返回移除结果
     */
    @Override
    public boolean persist(String key) {
        return redisTemplate.persist(key);
    }

    /**
     * 指定时间点设置过期时间的方法
     *
     * @param key  待设置过期时间的 KEY
     * @param date 过期的时间点
     * @return 返回设置结果
     */
    @Override
    public boolean expireAt(String key, final Date date) {
        return redisTemplate.expireAt(key, date);
    }

    /**
     * 获取匹配的 KEY 数量的方法
     *
     * @param keys KEY 集合
     * @return 返回匹配的 KEY 的个数
     */
    @Override
    public Long countExistingKeys(Collection<String> keys) {
        return redisTemplate.countExistingKeys(keys);
    }


    /**
     * 获取指定Key的类型的方法
     *
     * @param key 需要获取类型的Key
     * @return 返回Key的类型枚举（KeyTypeEnum），如果Key不存在或类型不支持则返回null
     */
    @Override
    @Nullable
    public KeyTypeEnum getKeyType(String key) {
        var type = redisTemplate.type(key);
        return switch (type) {
            case STRING -> KeyTypeEnum.STRING;
            case LIST -> KeyTypeEnum.LIST;
            case SET -> KeyTypeEnum.SET;
            case HASH -> KeyTypeEnum.HASH;
            default -> null;
        };
    }

}
