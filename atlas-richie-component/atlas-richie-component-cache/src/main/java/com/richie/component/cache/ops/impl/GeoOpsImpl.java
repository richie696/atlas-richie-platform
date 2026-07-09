/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.ops.impl;

import com.richie.component.cache.commons.GeoPointResult;
import com.richie.component.cache.function.GeoFunction;
import com.richie.component.cache.ops.GeoOps;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GeoOpsImpl implements GeoOps {

    private final GeoFunction fn;

    @Override
    public void add(String key, double longitude, double latitude, String member) {
        fn.addGeo(key, longitude, latitude, member);
    }

    @Override
    public Distance distance(String key, String member1, String member2) {
        return fn.geoDist(key, member1, member2);
    }

    @Override
    public List<GeoPointResult> radius(String key, double longitude, double latitude, double radius) {
        return fn.geoRadius(key, longitude, latitude, radius);
    }
}
