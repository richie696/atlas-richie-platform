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
package com.richie.component.cache.redis.manage;

import com.richie.component.cache.commons.GeoPointResult;
import com.richie.component.cache.function.GeoFunction;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GEO地理位置相关API管理器，封装了Redis中GEO数据结构的常用操作。
 * <p>
 * 主要用于地理位置的存储、距离计算、范围查询等场景。
 * 支持添加地理位置、计算成员间距离、范围查找等功能。
 * <p>
 * 适用于LBS、附近的人、地图服务等应用。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-25 17:38:22
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisGeoManager implements GeoFunction {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** Redis 性能守卫（可选启用） */
    private final RedisPerfGuard redisPerfGuard;

    /**
     * 添加地理位置数据到Redis GEO集合。
     *
     * @param key       GEO集合的键
     * @param longitude 经度
     * @param latitude  纬度
     * @param member    成员名称
     */
    @Override
    public void addGeo(String key, double longitude, double latitude, String member) {
        redisPerfGuard.execute("RedisGeoManager", "addGeo", RedisOperationCatalog.GEO_ADD, () -> {
            redisTemplate.opsForGeo().add(key, new Point(longitude, latitude), member);
        });
    }

    /**
     * 计算两个成员之间的地理距离。
     *
     * @param key     GEO集合的键
     * @param member1 成员1名称
     * @param member2 成员2名称
     * @return 两成员之间的距离对象，单位默认为米
     */
    @Override
    public Distance geoDist(String key, String member1, String member2) {
        return redisPerfGuard.<Distance>execute("RedisGeoManager", "geoDist", RedisOperationCatalog.GEO_DIST,
                () -> redisTemplate.opsForGeo().distance(key, member1, member2));
    }

    /**
     * 查询指定经纬度为圆心、指定半径范围内的所有成员，返回业务无关的GeoPointResult列表。
     *
     * @param key       GEO集合的键
     * @param longitude 圆心经度
     * @param latitude  圆心纬度
     * @param radius    查询半径（单位：公里）
     * @return 范围内的成员及其地理信息列表（业务无关Bean）
     */
    @Override
    public List<GeoPointResult> geoRadius(String key, double longitude, double latitude, double radius) {
        return redisPerfGuard.<List<GeoPointResult>>execute("RedisGeoManager", "geoRadius", RedisOperationCatalog.GEO_RADIUS, () -> {
            // 指定返回坐标信息（WITHCOORD）和距离信息（WITHDIST）
            var args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                    .includeCoordinates()  // 包含坐标信息
                    .includeDistance();     // 包含距离信息

            var results = redisTemplate.opsForGeo().radius(
                    key,
                    new Circle(new Point(longitude, latitude), new Distance(radius, Metrics.KILOMETERS)),
                    args
            );
            if (results == null) {
                return List.<GeoPointResult>of();
            }
            return results.getContent().stream()
                    .map(r -> {
                        var geo = r.getContent();
                        var point = geo.getPoint();
                        return new GeoPointResult(
                                (String) geo.getName(),
                                point.getX(),
                                point.getY(),
                                r.getDistance().getValue()
                        );
                    })
                    .toList();
        });
    }
}
