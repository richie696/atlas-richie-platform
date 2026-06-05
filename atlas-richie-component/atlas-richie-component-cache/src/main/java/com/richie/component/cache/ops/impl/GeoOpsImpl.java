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
