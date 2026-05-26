package com.richie.component.cache.function;

import com.richie.component.cache.commons.GeoPointResult;
import org.springframework.data.geo.Distance;

import java.util.List;

/**
 * GEO地理位置相关API管理器接口，封装了Redis中GEO数据结构的常用操作。
 * <p>
 * 主要用于地理位置的存储、距离计算、范围查询等场景。
 * 支持添加地理位置、计算成员间距离、范围查找等功能。
 * <p>
 * 适用于LBS、附近的人、地图服务等应用。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 17:38:22
 */
public interface GeoFunction extends CacheFunction {
    /**
     * 添加地理位置数据到Redis GEO集合。
     *
     * @param key      GEO集合的键
     * @param longitude 经度
     * @param latitude  纬度
     * @param member    成员名称
     */
    void addGeo(String key, double longitude, double latitude, String member);

    /**
     * 计算两个成员之间的地理距离。
     *
     * @param key     GEO集合的键
     * @param member1 成员1名称
     * @param member2 成员2名称
     * @return 两成员之间的距离对象，单位默认为米
     */
    Distance geoDist(String key, String member1, String member2);

    /**
     * 查询指定经纬度为圆心、指定半径范围内的所有成员，返回业务无关的GeoPointResult列表。
     *
     * @param key       GEO集合的键
     * @param longitude 圆心经度
     * @param latitude  圆心纬度
     * @param radius    查询半径（单位：公里）
     * @return 范围内的成员及其地理信息列表（业务无关Bean）
     */
    List<GeoPointResult> geoRadius(String key, double longitude, double latitude, double radius);
}
