/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.context.utils.security;

import cn.richie696.context.utils.data.JsonUtils;
import tools.jackson.core.type.TypeReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;


/**
 * 签名工具类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-11-04 14:05:03
 */
public final class SignatureUtils {

    /**
     * 请求签名协议版本。
     * <p>
     * {@link #LEGACY_MD5} 仅用于存量客户端的迁移窗口，不能作为新接口的默认值。
     * 新接入方应使用 {@link #HMAC_SHA256_V2}。
     */
    public enum SignatureVersion {
        /**
         * 原有协议：{@code MD5(url + "?" + sortedParams + secret)}。
         */
        LEGACY_MD5("v1", "MD5"),

        /**
         * 新协议：对带版本域分隔的规范化字符串执行 HMAC-SHA256。
         */
        HMAC_SHA256_V2("v2", "HMAC-SHA256");

        private final String wireValue;
        private final String algorithm;

        SignatureVersion(String wireValue, String algorithm) {
            this.wireValue = wireValue;
            this.algorithm = algorithm;
        }

        /**
         * 用于请求头或协议文档的版本标识，例如 {@code v2}。
         */
        public String wireValue() {
            return wireValue;
        }

        /**
         * 当前版本使用的摘要或 MAC 算法名称。
         */
        public String algorithm() {
            return algorithm;
        }
    }

    private SignatureUtils() {
    }

    /**
     * 创建请求签名的方法
     *
     * @param paramMap 待签名的参数
     * @param url 请求的Rest接口
     * @param secretKey 签名KEY
     * @return 返回创建的签名
     */
    @Deprecated(since = "1.0.0", forRemoval = false)
    public static String createSign(Map<String, Object> paramMap, String url, String secretKey) {
        return createSign(paramMap, url, secretKey, SignatureVersion.LEGACY_MD5);
    }

    /**
     * 使用指定协议版本创建请求签名。
     * <p>
     * 新接口必须传入 {@link SignatureVersion#HMAC_SHA256_V2}；版本应由调用入口的
     * 服务端配置确定，而不是根据请求体内容猜测。
     *
     * @param paramMap 待签名的参数
     * @param url 请求的 Rest 接口
     * @param secretKey 签名密钥
     * @param version 签名协议版本
     * @return 签名值
     */
    public static String createSign(Map<String, Object> paramMap, String url, String secretKey,
                                    SignatureVersion version) {
        return generateSignature(url, toSortedParamString(paramMap), secretKey, version);
    }

    /**
     * 校验签名的方法
     *
     * @param jsonString 待校验的参数字符串
     * @param url 请求的Rest接口
     * @param secretKey 签名KEY
     * @return 返回检查结果
     */
    @Deprecated(since = "1.0.0", forRemoval = false)
    public static boolean checkSign(String jsonString, String url, String secretKey) {
        return checkSign(jsonString, url, secretKey, SignatureVersion.LEGACY_MD5);
    }

    /**
     * 使用服务端指定的协议版本校验 JSON 请求中的 {@code sign} 字段。
     * <p>
     * 调用方应根据已配置的客户端迁移状态选择 {@code version}。不要将客户端自报的
     * 版本直接作为自动降级依据；迁移期如需兼容，应由入口显式维护允许版本集合。
     *
     * @param jsonString 待校验的参数字符串，其中 {@code sign} 不参与规范化
     * @param url 请求的 Rest 接口
     * @param secretKey 签名密钥
     * @param version 服务端期望的签名协议版本
     * @return 签名是否匹配
     */
    public static boolean checkSign(String jsonString, String url, String secretKey,
                                    SignatureVersion version) {
        Map<String, Object> paramMap = mapDeleteNull(jsonString);
        Object signValue = paramMap.remove("sign");
        if (!(signValue instanceof String sign) || sign.isBlank()) {
            return false;
        }
        return constantTimeEquals(
                generateSignature(url, toSortedParamString(paramMap), secretKey, version), sign);
    }

    // ========== DTO / VO 签名（基于 @SignField 注解） ==========

    /**
     * 通过 DTO/VO 对象创建签名
     * <p>提取 {@link SignField @SignField} 标注的字段，按 order + 字段名排序后生成签名。</p>
     *
     * @param dto       待签名的 DTO/VO 对象
     * @param url       请求的 Rest 接口
     * @param secretKey 签名 KEY
     * @param <T>       DTO/VO 类型
     * @return 返回创建的签名
     */
    @Deprecated(since = "1.0.0", forRemoval = false)
    public static <T> String createSign(T dto, String url, String secretKey) {
        return createSign(dto, url, secretKey, SignatureVersion.LEGACY_MD5);
    }

    /**
     * 使用指定协议版本创建 DTO/VO 请求签名。
     */
    public static <T> String createSign(T dto, String url, String secretKey, SignatureVersion version) {
        return generateSignature(url, buildSortedStringFromDTO(dto), secretKey, version);
    }

    /**
     * 校验 DTO/VO 对象的签名
     *
     * @param dto       待校验的 DTO/VO 对象
     * @param url       请求的 Rest 接口
     * @param secretKey 签名 KEY
     * @param sign      待校验的签名值
     * @param <T>       DTO/VO 类型
     * @return 签名验证结果
     */
    @Deprecated(since = "1.0.0", forRemoval = false)
    public static <T> boolean checkSign(T dto, String url, String secretKey, String sign) {
        return checkSign(dto, url, secretKey, sign, SignatureVersion.LEGACY_MD5);
    }

    /**
     * 使用服务端指定的协议版本校验 DTO/VO 签名。
     */
    public static <T> boolean checkSign(T dto, String url, String secretKey, String sign,
                                        SignatureVersion version) {
        if (sign == null || sign.isBlank()) {
            return false;
        }
        String computedSign = generateSignature(url, buildSortedStringFromDTO(dto), secretKey, version);
        return constantTimeEquals(computedSign, sign);
    }

    /**
     * 从 DTO 中提取 @SignField 字段，按 order + 字段名排序，生成参数字符串
     */
    private static String buildSortedStringFromDTO(Object dto) {
        if (dto == null) {
            return "";
        }
        SignConfig config = resolveSignConfig(dto.getClass());
        return buildSortedStringFromDTO(dto, config);
    }

    private static String buildSortedStringFromDTO(Object dto, SignConfig config) {
        List<Field> fields = getAnnotatedFields(dto.getClass());
        if (fields.isEmpty()) {
            return "";
        }
        // 排序：order 升序 → 字段名字典序
        fields.sort(Comparator.<Field, Integer>comparing(f -> f.getAnnotation(SignField.class).order())
                .thenComparing(Field::getName));

        // 校验自定义 order 不可重复（order=0 为默认值，不参与校验）
        Set<Integer> seenOrders = new HashSet<>();
        for (Field field : fields) {
            int order = field.getAnnotation(SignField.class).order();
            if (order != 0 && !seenOrders.add(order)) {
                throw new IllegalArgumentException(
                        "Duplicate @SignField order=%d on field '%s' (and potentially others)".formatted(order, field.getName()));
            }
        }

        String connector = config.connector();
        boolean includeFieldName = config.includeFieldName();

        StringBuilder paramString = new StringBuilder();
        try {
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(dto);
                SignField signField = field.getAnnotation(SignField.class);
                String key = signField.name().isEmpty() ? field.getName() : signField.name();

                if (value == null) {
                    appendField(paramString, key, "null", connector, includeFieldName);
                } else if (isSimpleType(value)) {
                    appendField(paramString, key, value.toString(), connector, includeFieldName);
                } else if (value instanceof Collection || value instanceof Map) {
                    throw new IllegalArgumentException(
                            "Collection/Map type not allowed for sign field: " + field.getName());
                } else {
                    // 嵌套 DTO：递归提取，使用嵌套 DTO 自身的 @SignConfig
                    SignConfig nestedConfig = resolveSignConfig(value.getClass());
                    String nested = buildSortedStringFromDTO(value, nestedConfig);
                    // 嵌套结构始终包含外层 key，便于区分所属关系
                    paramString.append(key).append('=').append('(').append(nested).append(')').append(connector);
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access sign field", e);
        }
        if (!paramString.isEmpty()) {
            paramString.deleteCharAt(paramString.length() - 1);
        }
        return paramString.toString();
    }

    /**
     * 按 {@code includeFieldName} 配置追加字段到参数字符串
     */
    private static void appendField(StringBuilder sb, String key, String value,
                                    String connector, boolean includeFieldName) {
        if (includeFieldName) {
            sb.append(key).append('=').append(value);
        } else {
            sb.append(value);
        }
        sb.append(connector);
    }

    /**
     * 获取类上的 @SignConfig，沿继承链向上查找（默认 {@code connector="&", includeFieldName=true}）
     */
    private static SignConfig resolveSignConfig(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            SignConfig config = current.getAnnotation(SignConfig.class);
            if (config != null) {
                return config;
            }
            current = current.getSuperclass();
        }
        // 使用不可变单例默认值
        return DefaultSignConfig.INSTANCE;
    }

    /**
     * &#064;SignConfig  的默认实现（connector="&", includeFieldName=true）
     */
    private static final class DefaultSignConfig implements SignConfig {

        private static final DefaultSignConfig INSTANCE = new DefaultSignConfig();

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return SignConfig.class;
        }

        @Override
        public String connector() {
            return "&";
        }

        @Override
        public boolean includeFieldName() {
            return true;
        }
    }

    /**
     * 获取类及其父类中所有 @SignField 标注的字段
     */
    private static List<Field> getAnnotatedFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(SignField.class)) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    /**
     * 判断类型是否为签名支持的"简单类型"
     * <p>简单类型直接序列化为字符串值，嵌套类型递归提取 @SignField 字段。</p>
     */
    private static boolean isSimpleType(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum;
    }

    private static Map<String, Object> mapDeleteNull(String jsonString) {
        //不要用FastJson去排除空字符串会导致二级参数顺序错乱签名不过
        Map<String, Object> map = JsonUtils.getInstance().deserialize(jsonString, new TypeReference<>() {
        });
        if (map == null) {
            return new HashMap<>();
        }
        Map<String, Object> copiedMap = new HashMap<>(map);
        copiedMap.entrySet().removeIf(item -> "".equals(item.getValue()));
        return copiedMap;
    }

    @SuppressWarnings("unchecked")
    private static String toSortedParamString(Map<String, Object> paramMap) {
        if (paramMap == null || paramMap.isEmpty()) {
            return "";
        }
        StringBuilder paramString = new StringBuilder();
        paramMap.keySet().stream().sorted().forEachOrdered(key -> {
            Object value = paramMap.get(key);
            if (value instanceof Map) {
                String str = toSortedParamString((Map<String, Object>) value);
                paramString.append(key).append('=').append('(').append(str).append(')').append('&');
            } else {
                paramString.append(key).append('=').append(value).append('&');
            }
        });
        return paramString.deleteCharAt(paramString.length() - 1).toString();
    }

    private static String generateSignature(String url, String sortedParamStr, String secret,
                                            SignatureVersion version) {
        Objects.requireNonNull(version, "signature version must not be null");
        return switch (version) {
            case LEGACY_MD5 -> generateLegacyMd5(url, sortedParamStr, secret);
            case HMAC_SHA256_V2 -> generateHmacSha256(url, sortedParamStr, secret, version);
        };
    }

    /**
     * 保持存量 MD5 字节串完全不变，保障迁移窗口内的老调用方兼容。
     */
    private static String generateLegacyMd5(String url, String sortedParamStr, String secret) {
        String origin = "%s?%s%s".formatted(url, sortedParamStr, secret);
        return HashUtils.md5(origin);
    }

    private static String generateHmacSha256(String url, String sortedParamStr, String secret,
                                              SignatureVersion version) {
        requireNonBlank(url, "url");
        requireNonBlank(secret, "secretKey");
        String canonical = "%s\n%s\n%s\n%s".formatted(
                version.wireValue(), version.algorithm(), url, sortedParamStr);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank for HMAC-SHA256 signatures");
        }
    }

}
