package com.richie.component.desensitize.core.config;

import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.model.MaskRule;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 脱敏组件配置：{@code platform.component.desensitize}。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.desensitize")
public class DesensitizeProperties {

    /**
     * 脱敏总开关。
     * <p>
     * 默认值：{@code true}（启用）。
     * <p>
     * 调整示例：
     * <pre>{@code
     * platform:
     *   component:
     *     desensitize:
     *       enabled: false
     * }</pre>
     */
    private boolean enabled = true;

    /**
     * 默认掩码字符。
     * <p>
     * 当具体规则未显式指定掩码符号时，统一使用该字符。
     * 默认值：{@code *}。
     * <p>
     * 调整示例：
     * <pre>{@code
     * platform:
     *   component:
     *     desensitize:
     *       default-mask-char: "#"
     * }</pre>
     */
    private char defaultMaskChar = '*';

    /**
     * 场景开关配置。
     * <p>
     * key 使用短横线形式（如 {@code api-response}、{@code log}）。
     * 默认值：{@code api-response/log/audit/exception} 全部为 {@code true}。
     * 未配置的场景按启用处理。
     * <p>
     * 调整示例：
     * <pre>{@code
     * platform:
     *   component:
     *     desensitize:
     *       scenes:
     *         log: false
     *         audit: true
     * }</pre>
     */
    private Map<String, Boolean> scenes = defaultScenes();

    /**
     * 脱敏权限配置。
     * <p>
     * 用于控制“哪些角色可查看明文”。
     */
    private Permission permission = new Permission();

    /**
     * 全局敏感键配置（按 Map key 命中）。
     * <p>
     * key 不区分大小写，内部会统一转小写匹配。
     * 默认值：空（不做全局 key 规则）。
     * <p>
     * 调整示例：
     * <pre>{@code
     * platform:
     *   component:
     *     desensitize:
     *       sensitive-keys:
     *         mobile: PHONE
     *         idCard: ID_CARD
     *         email: EMAIL
     * }</pre>
     */
    private Map<String, MaskType> sensitiveKeys = new HashMap<>();

    /**
     * 按脱敏类型定义的规则模板。
     * <p>
     * key 为 {@link MaskType}，value 为该类型的默认规则。
     * 用于统一控制“保留位数、掩码字符”等策略参数，避免在字段级重复配置。
     * 默认值：为每个 {@link MaskType} 预置默认左右保留位；掩码字符默认跟随全局
     * {@code default-mask-char}。
     * <p>
     * 调整示例：
     * <pre>{@code
     * platform:
     *   component:
     *     desensitize:
     *       type-rules:
     *         PHONE:
     *           keep-left: 3
     *           keep-right: 4
     *           mask-char: "*"
     *         ID_CARD:
     *           keep-left: 6
     *           keep-right: 4
     * }</pre>
     */
    private Map<MaskType, TypeRule> typeRules = defaultTypeRules();

    /**
     * 按类字段声明的精细化规则。
     * <p>
     * 第一层 key：类全限定名；第二层 key：字段名；value：脱敏类型。
     * 默认值：空（仅依赖注解或 sensitive-keys）。
     * <p>
     * 调整示例：
     * <pre>{@code
     * platform:
     *   component:
     *     desensitize:
     *       fields:
     *         com.example.UserDTO:
     *           phone: PHONE
     *           bankCardNo: BANK_CARD
     * }</pre>
     */
    private Map<String, Map<String, MaskType>> fields = new HashMap<>();

    /**
     * API 返回场景覆盖配置。
     * <p>
     * 用于覆盖全局 {@code sensitive-keys}，仅在 {@code API_RESPONSE} 场景生效。
     */
    private SceneOverride apiResponse = new SceneOverride();

    /**
     * 日志场景覆盖配置。
     * <p>
     * 用于覆盖全局 {@code sensitive-keys}，在 {@code LOG/AUDIT} 场景生效。
     */
    private LogSceneOverride log = new LogSceneOverride();

    /**
     * 日志文本正则兜底配置。
     * <p>
     * 当结构化规则无法覆盖时，可通过正则进行补充脱敏。
     */
    private LogRegexFallback logRegexFallback = new LogRegexFallback();

    /**
     * 异常文本正则兜底配置。
     * <p>
     * 主要用于异常堆栈或错误消息中的敏感信息清洗。
     */
    private ExceptionRegexFallback exceptionRegexFallback = new ExceptionRegexFallback();

    /**
     * 判断指定场景是否启用。
     *
     * @param scene 场景枚举
     * @return 是否启用
     */
    public boolean isSceneEnabled(MaskScene scene) {
        String key = toKebab(scene.name());
        Boolean value = scenes.get(key);
        return value == null || value;
    }

    private static String toKebab(String name) {
        return name.toLowerCase().replace('_', '-');
    }

    private static Map<String, Boolean> defaultScenes() {
        Map<String, Boolean> map = new HashMap<>();
        map.put("api-response", true);
        map.put("log", true);
        map.put("audit", true);
        map.put("exception", true);
        return map;
    }

    private static Map<MaskType, TypeRule> defaultTypeRules() {
        Map<MaskType, TypeRule> map = new EnumMap<>(MaskType.class);
        for (MaskType type : MaskType.values()) {
            TypeRule rule = new TypeRule();
            rule.setKeepLeft(MaskRule.defaultKeepLeft(type));
            rule.setKeepRight(MaskRule.defaultKeepRight(type));
            rule.setMaskChar(null);
            map.put(type, rule);
        }
        return map;
    }

    /**
     * 权限相关配置。
     *
     * @author @richie696
     * @since 1.0.0
     * @version 1.0
     */
    @Data
    @ConfigurationProperties(prefix = "platform.component.desensitize.permission")
    public static class Permission {
        /**
         * 权限控制开关。
         * <p>
         * 默认值：{@code false}（关闭，所有请求默认脱敏）。
         * 开启后，会结合 {@link #plainTextRoles} 判断是否可返回明文。
         */
        private boolean enabled;

        /**
         * 明文可见角色集合。
         * <p>
         * 仅当 {@link #enabled} 为 {@code true} 时生效。
         * 命中任一角色则跳过脱敏，返回原文。
         * 默认值：空集合（即使启用权限控制，也不会放行明文）。
         * <p>
         * 调整示例：
         * <pre>{@code
         * platform:
         *   component:
         *     desensitize:
         *       permission:
         *         enabled: true
         *         plain-text-roles:
         *           - ROLE_SELF_PROFILE
         *           - ROLE_AUDIT_PLAINTEXT
         * }</pre>
         */
        private Set<String> plainTextRoles = new HashSet<>();
    }

    /**
     * API场景覆盖配置。
     *
     * @author @richie696
     * @since 1.0.0
     * @version 1.0
     */
    @Data
    @ConfigurationProperties(prefix = "platform.component.desensitize.api")
    public static class SceneOverride {
        /**
         * 当前场景的敏感键覆盖表。
         * <p>
         * key 为 Map 字段名，value 为脱敏类型。
         * 默认值：空（即不覆盖全局配置）。
         * <p>
         * 调整示例：
         * <pre>{@code
         * platform:
         *   component:
         *     desensitize:
         *       api-response:
         *         sensitive-keys:
         *           phone: PHONE
         *           email: EMAIL
         * }</pre>
         */
        private Map<String, MaskType> sensitiveKeys = new HashMap<>();
    }
    /**
     * 日志场景覆盖配置。
     *
     * @author @richie696
     * @since 1.0.0
     * @version 1.0
     */
    @ConfigurationProperties(prefix = "platform.component.desensitize.log")
    public static class LogSceneOverride extends SceneOverride {
    }

    /**
     * 正则兜底配置。
     *
     * @author @richie696
     * @since 1.0.0
     * @version 1.0
     */
    @Data
    @ConfigurationProperties(prefix = "platform.component.desensitize.log-regex-fallback")
    public static class LogRegexFallback {
        /**
         * 正则兜底开关。
         * <p>
         * 默认值：{@code false}（关闭）。
         */
        private boolean enabled;

        /**
         * 脱敏类型到正则表达式的映射。
         * <p>
         * key 为 {@link MaskType}，value 为对应匹配正则。
         * 默认值：空映射（无兜底规则）。
         * <p>
         * 调整示例：
         * <pre>{@code
         * platform:
         *   component:
         *     desensitize:
         *       log-regex-fallback:
         *         enabled: true
         *         rules:
         *           PHONE: "(?<!\\d)1\\d{10}(?!\\d)"
         *           EMAIL: "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
         * }</pre>
         */
        private Map<MaskType, String> rules = new EnumMap<>(MaskType.class);
    }

    /**
     * 正则兜底配置。
     *
     * @author @richie696
     * @since 1.0.0
     * @version 1.0
     */
    @Data
    @ConfigurationProperties(prefix = "platform.component.desensitize.exception-regex-fallback")
    public static class ExceptionRegexFallback {
        /**
         * 正则兜底开关。
         * <p>
         * 默认值：{@code false}（关闭）。
         */
        private boolean enabled;

        /**
         * 脱敏类型到正则表达式的映射。
         * <p>
         * key 为 {@link MaskType}，value 为对应匹配正则。
         * 默认值：空映射（无兜底规则）。
         * <p>
         * 调整示例：
         * <pre>{@code
         * platform:
         *   component:
         *     desensitize:
         *       log-regex-fallback:
         *         enabled: true
         *         rules:
         *           PHONE: "(?<!\\d)1\\d{10}(?!\\d)"
         *           EMAIL: "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
         * }</pre>
         */
        private Map<MaskType, String> rules = new EnumMap<>(MaskType.class);
    }

    /**
     * 单个 {@link MaskType} 的规则配置。
     *
     * @author @richie696
     * @since 1.0.0
     * @version 1.0
     */
    @Data
    public static class TypeRule {
        /**
         * 左侧保留位数。
         * <p>
         * 默认值：使用该 {@link MaskType} 的内置默认保留位。
         */
        private Integer keepLeft;

        /**
         * 右侧保留位数。
         * <p>
         * 默认值：使用该 {@link MaskType} 的内置默认保留位。
         */
        private Integer keepRight;

        /**
         * 当前类型专属掩码字符。
         * <p>
         * 默认值：{@code null}（回退到全局 {@code default-mask-char}）。
         */
        private Character maskChar;
    }
}
