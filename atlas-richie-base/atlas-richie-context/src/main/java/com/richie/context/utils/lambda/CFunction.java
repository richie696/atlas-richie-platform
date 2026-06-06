package com.richie.context.utils.lambda;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 可序列化的函数式接口，用于在 Lambda 表达式中安全地引用实体类的字段。
 * <p>
 * 典型用法：配合 {@link LambdaUtils} 在 MyBatis-Plus、Elasticsearch 等场景下
 * 通过 {@code User::getName} 这种方法引用获取对应的实体字段名，
 * 避免硬编码字段名字符串导致的拼写错误。
 * <p>
 * 之所以继承 {@link Serializable}，是因为某些框架（如 MyBatis-Plus 的
 * {@code LambdaQueryWrapper}）会在反序列化场景下使用 Lambda 表达式，
 * 普通 {@link Function} 不支持序列化会导致运行期异常。
 *
 * @param <T> 实体类型
 * @param <R> 属性类型
 * @author richie696
 * @since 2026-06-07
 */
@FunctionalInterface
public interface CFunction<T, R> extends Function<T, R>, Serializable {
}
