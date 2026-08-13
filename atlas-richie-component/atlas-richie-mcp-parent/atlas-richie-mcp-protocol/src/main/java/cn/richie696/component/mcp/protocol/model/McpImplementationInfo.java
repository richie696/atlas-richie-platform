package cn.richie696.component.mcp.protocol.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 对端实现身份描述（名称/版本/标题/描述/网址/图标）。
 *
 * 为什么用 record：MCP 在 2025-11-25 与 2026-07-28 两个协议时代都要求上报
 * "我是谁"信息（{@code clientInfo} / {@code serverInfo}），但字段集基本一致。
 * 把它建模为不可变 record：
 * <ul>
 *   <li>保证一旦进入业务层就不可被外部修改，避免污染审计/可观测链路。</li>
 *   <li>提供 {@link #fromWire(Map)} / {@link #toWire()} 双向转换，把"JSON 解析产物"
 *       与"领域对象"明确分开——这是防腐层（anti-corruption layer）通用做法。</li>
 * </ul>
 *
 * 关键设计：
 * <ul>
 *   <li>{@code name / version} 强制非空，{@code title / description / websiteUrl} 可选。</li>
 *   <li>{@code icons} 元素为 {@code Map<String, Object>}，每个内层 Map 会被
 *       拷贝为 {@link Collections#unmodifiableMap} 防止外部修改影响快照。</li>
 *   <li>{@link #toWire()} 使用 {@link LinkedHashMap} 保证字段顺序稳定，方便测试断言。</li>
 * </ul>
 *
 * @param name        实现名称（必填，非空）
 * @param version     实现版本（必填，非空）
 * @param title       可读的展示标题
 * @param description 可读的描述
 * @param websiteUrl  官方网站/仓库地址
 * @param icons       图标描述列表，每个元素为任意键值对
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpImplementationInfo(
        String name,
        String version,
        String title,
        String description,
        String websiteUrl,
        List<Map<String, Object>> icons) {

    /**
     * 便捷构造：仅指定必填字段 {@code name / version}，其他可选字段留空。
     *
     * @param name    实现名称
     * @param version 实现版本
     */
    public McpImplementationInfo(String name, String version) {
        this(name, version, null, null, null, List.of());
    }

    /**
     * 紧凑构造器：校验必填字段，并把 {@code icons} 归一为不可变列表。
     */
    public McpImplementationInfo {
        name = Objects.requireNonNull(name, "name");
        version = Objects.requireNonNull(version, "version");
        icons = immutableIcons(icons);
    }

    /**
     * 从线格式 {@code Map}（如 JSON 反序列化产物）构造实现信息。
     *
     * @param source 线格式源数据，键名遵循 {@code toWire} 输出约定
     * @return 不可变的领域对象
     * @throws IllegalArgumentException 当 {@code name / version} 缺失或非字符串、
     *                                  {@code icons} 不是数组或含非字符串键时
     */
    public static McpImplementationInfo fromWire(Map<String, Object> source) {
        return new McpImplementationInfo(
                requiredString(source.get("name"), "name"),
                requiredString(source.get("version"), "version"),
                optionalString(source.get("title"), "title"),
                optionalString(source.get("description"), "description"),
                optionalString(source.get("websiteUrl"), "websiteUrl"),
                icons(source.get("icons")));
    }

    /**
     * 转为线格式 {@code Map}（不可变），字段顺序固定为
     * {@code name, version, title, description, websiteUrl, icons}。
     *
     * @return 不可变的线格式 Map
     */
    public Map<String, Object> toWire() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("version", version);
        putIfPresent(result, "title", title);
        putIfPresent(result, "description", description);
        putIfPresent(result, "websiteUrl", websiteUrl);
        if (!icons.isEmpty()) {
            result.put("icons", icons);
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<Map<String, Object>> icons(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException("icons must be an array");
        }
        List<Map<String, Object>> result = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> icon)) {
                throw new IllegalArgumentException("icons[] must be an object");
            }
            Map<String, Object> typed = new LinkedHashMap<>();
            icon.forEach((key, entryValue) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException("icons[] contains a non-string key");
                }
                typed.put(stringKey, entryValue);
            });
            result.add(Collections.unmodifiableMap(typed));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> immutableIcons(List<Map<String, Object>> icons) {
        return icons == null ? List.of() : icons(icons);
    }

    private static String requiredString(Object value, String field) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return string;
    }

    private static String optionalString(Object value, String field) {
        return value == null ? null : requiredString(value, field);
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
