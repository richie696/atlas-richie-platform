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
