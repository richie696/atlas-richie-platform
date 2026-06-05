package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.BitmapFunction;
import com.richie.component.cache.ops.BitmapOps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BitmapOpsImpl implements BitmapOps {

    private final BitmapFunction fn;

    @Override
    public void set(String key, long offset, boolean value) {
        fn.setBit(key, offset, value);
    }

    @Override
    public boolean get(String key, long offset) {
        return fn.getBit(key, offset);
    }
}
