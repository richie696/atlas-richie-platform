package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.HyperLogFunction;
import com.richie.component.cache.ops.HyperLogOps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HyperLogOpsImpl implements HyperLogOps {

    private final HyperLogFunction fn;

    @Override
    public void add(String key, Object... values) {
        fn.pfAdd(key, values);
    }

    @Override
    public long count(String key) {
        return fn.pfCount(key);
    }
}
