package com.richie.component.cache.ops;

import com.richie.component.cache.commons.GeoPointResult;
import org.springframework.data.geo.Distance;

import java.util.List;

/**
 * 地理位置操作接口。
 * <p>对应底层 Geo 数据结构，支持地理位置存储、距离计算及半径查询。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-05
 */
public interface GeoOps {

    void add(String key, double longitude, double latitude, String member);

    Distance distance(String key, String member1, String member2);

    List<GeoPointResult> radius(String key, double longitude, double latitude, double radius);
}
