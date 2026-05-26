package com.richie.component.web.i18n;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.ZoneId;
import java.util.Map;

/**
 * 获取映射门店和时区的关系，需要自己实现
 *
 * <p>
 * 1. 在{@code void run(ApplicationArguments args)}方法初始化{@code Map<String, String>}，映射门店和时区的关系
 * 2. {@code getShopCodeTimezoneMap()}方法映射门店和时区的关系
 * 例：
 * {@code
 *
 * @author yuy
 * @version 1.0
 * @Service
 * @RequiredArgsConstructor public class MyShopTimezoneProvider extends ShopTimezoneProvider {
 * private final ShopTimezoneMapper shopTimezoneMapper;
 * @Override public Map<String, String> getShopCodeTimezoneMap() {
 * List<ShopTimezone> list = ChainWrappers.lambdaQueryChain(shopTimezoneMapper).list();
 * return Collection2MapUtils.collection2Map(list, x->String.valueOf(x.getShopCode()), ShopTimezone::getTimezone);
 * }
 * }
 * }
 * @since 2023-10-11 1:13:04
 */
@RequiredArgsConstructor
public abstract class ShopTimezoneProvider implements ApplicationRunner {
    /**
     * 分隔符
     */
    public static final String SPLIT_TOKEN = "-";

    private static final String REDIS_KEY = "ShopCodeTimezoneMap";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 应用启动时将子类提供的门店/时区映射写入 Redis。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        putAll(getShopCodeTimezoneMap());
    }

    /**
     * 新增单个门店/时区映射。
     *
     * @param hashKey 键（如租户-门店）
     * @param value   时区 ID 字符串
     */
    public void addShopCodeTimezone(String hashKey, String value) {
        redisTemplate.opsForHash().put(REDIS_KEY, hashKey, value);
    }

    /**
     * 批量写入门店/时区映射。
     *
     * @param timezoneMap 键为门店键，值为时区 ID
     */
    public void putAll(Map<String, String> timezoneMap) {
        redisTemplate.opsForHash().putAll(REDIS_KEY, timezoneMap);
    }

    /**
     * 根据键查询时区字符串。
     *
     * @param hashKey 键（如租户-门店）
     * @return 时区 ID，不存在为 null
     */
    public String getShopCodeTimezone(String hashKey) {
        return (String) redisTemplate.opsForHash().get(REDIS_KEY, hashKey);
    }

    /**
     * 删除指定键的门店/时区映射。
     *
     * @param hashKeys 键列表
     * @return 删除的条数
     */
    public Long delShopCodeTimezone(Object... hashKeys) {
        return redisTemplate.opsForHash().delete(REDIS_KEY, hashKeys);
    }

    /**
     * 根据键获取时区。
     *
     * @param key 键（如租户-门店）
     * @return 时区，不存在或无效为 null
     */
    public ZoneId getZoneIdByKey(String key) {
        if (StringUtils.isNotBlank(key)) {
            String dbTimezoneStr = (String) redisTemplate.opsForHash().get(REDIS_KEY, key);
            if (StringUtils.isNotBlank(dbTimezoneStr)) {
                return ZoneId.of(dbTimezoneStr);
            }
        }
        return null;
    }

    /**
     * 从外部获取门店和时区的映射关系，需要子类实现
     */
    public abstract Map<String, String> getShopCodeTimezoneMap();

}
