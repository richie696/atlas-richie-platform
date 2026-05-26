package com.richie.component.search.model;

import java.io.Serializable;
import java.util.function.Function;

@FunctionalInterface
public interface CFunction<T, R> extends Function<T, R>, Serializable {
}
