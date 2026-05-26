package com.richie.component.liquibase.migration;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 供各组件按需注册自己的 changelog 路径。
 * <p>
 * 与配置中的 changeLogs 配合使用，组件可在初始化时通过此类注册额外的 changelog 位置。
 *
 * @author richie696
 * @since 2025-12-15
 */
public class ChangeLogRegistry {

    private final Set<String> locations = new LinkedHashSet<>();

    /**
     * 默认构造函数（供 Spring 注入使用）。
     */
    public ChangeLogRegistry() {
    }

    /**
     * 注册一条 changelog 路径（null 或空白会被忽略）。
     *
     * @param location changelog 路径（如 classpath 或文件 URI）
     */
    public void add(String location) {
        if (location != null && !location.isBlank()) {
            locations.add(location);
        }
    }

    /**
     * 返回已注册的所有 changelog 路径的不可变副本。
     *
     * @return 已注册路径集合
     */
    public Set<String> getAll() {
        return Set.copyOf(locations);
    }

    /**
     * 判断是否未注册任何路径。
     *
     * @return 未注册任何路径时返回 true
     */
    public boolean isEmpty() {
        return locations.isEmpty();
    }
}
