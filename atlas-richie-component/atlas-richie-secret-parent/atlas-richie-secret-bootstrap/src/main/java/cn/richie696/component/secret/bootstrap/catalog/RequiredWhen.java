/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.catalog;

import java.util.List;

/**
 * Binding 的条件必需规则。
 */
public record RequiredWhen(String property, List<String> in) {
    public RequiredWhen {
        in = in == null ? List.of() : List.copyOf(in);
    }
}
