package com.richie.component.mongodb.builder;

import java.io.Serializable;
import java.util.function.Function;

@FunctionalInterface
public interface LambdaField<T, R> extends Function<T, R>, Serializable {
}
