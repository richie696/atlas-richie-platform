/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.context.utils.security;

import com.richie.context.utils.data.Collections;
import com.richie.context.utils.data.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

import java.lang.reflect.Field;
import java.util.*;


/**
 * 签名工具类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-11-04 14:05:03
 */
@Slf4j
public final class SignatureUtils {

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
    public static String createSign(Map<String, Object> paramMap, String url, String secretKey) {
        String sortMap = toSortedParamString(paramMap);
        return generateSig(url, sortMap, secretKey);
    }

    /**
     * 校验签名的方法
     *
     * @param jsonString 待校验的参数字符串
     * @param url 请求的Rest接口
     * @param secretKey 签名KEY
     * @return 返回检查结果
     */
    public static boolean checkSign(String jsonString, String url, String secretKey) {
        Map<String, Object> paramMap = mapDeleteNull(jsonString);
        String sign = (String) paramMap.remove("sign");

        String sortMap = toSortedParamString(paramMap);
        //加密请求参数
        String signature = generateSig(url, sortMap, secretKey);
        log.info("传入签名【{}】，自签名【{}】，匹配【{}】", sign, signature, (sign != null && sign.equals(signature)));
        return signature.equals(sign);
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
    public static <T> String createSign(T dto, String url, String secretKey) {
        String sortMap = buildSortedStringFromDTO(dto);
        return generateSig(url, sortMap, secretKey);
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
    public static <T> boolean checkSign(T dto, String url, String secretKey, String sign) {
        String sortMap = buildSortedStringFromDTO(dto);
        String computedSign = generateSig(url, sortMap, secretKey);
        log.info("传入签名【{}】，自签名【{}】，匹配【{}】", sign, computedSign, computedSign.equals(sign));
        return computedSign.equals(sign);
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
            return Collections.mapOf();
        }
        map.entrySet().removeIf(item -> "".equals(item.getValue()));
        return map;
    }

    @SuppressWarnings("unchecked")
    private static String toSortedParamString(Map<String, Object> paramMap) {
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

    private static String generateSig(String url, String sortedParamStr, String secret) {
        String origin = "%s?%s%s".formatted(url, sortedParamStr, secret);
        String sig = HashUtils.md5(origin);
        log.info("================generateSig Begin====================");
        log.info("|{}|", origin);
        log.info("|{}|", sig);
        log.info("================generateSig End======================");
        return sig;
    }

}
