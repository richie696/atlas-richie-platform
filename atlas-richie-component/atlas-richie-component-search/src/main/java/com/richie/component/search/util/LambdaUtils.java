package com.richie.component.search.util;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Lambda 工具类，用于解析 Lambda 表达式中的字段名。
 * 参考 MyBatis-Plus 的实现方式。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
public class LambdaUtils {

    /**
     * 从 Lambda 表达式获取字段名
     *
     * @param column Lambda 表达式
     * @return 字段名
     */
    public static String getFieldName(SerializedLambda column) {
        try {
            String methodName = column.getImplMethodName();
            String fieldName = methodName.substring(3);
            fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
            return fieldName;
        } catch (Exception e) {
            throw new RuntimeException("无法解析字段名", e);
        }
    }

    /**
     * 从 Lambda 表达式获取字段名（简化版本）
     *
     * @param column Lambda 表达式
     * @param <T> 实体类型
     * @return 字段名
     */
    public static <T> String getFieldName(Function<T, ?> column) {
        try {
            // 使用反射获取方法名
            Method method = column.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) method.invoke(column);
            return getFieldName(lambda);
        } catch (Exception e) {
            // 如果无法通过 SerializedLambda 获取，使用简化方法
            return getFieldNameSimple(column);
        }
    }

    /**
     * 简化的字段名获取方法
     *
     * @param column Lambda 表达式
     * @param <T> 实体类型
     * @return 字段名
     */
    private static <T> String getFieldNameSimple(Function<T, ?> column) {
        try {
            // 获取 Lambda 表达式的类名
            String className = column.getClass().getName();

            // Lambda 表达式的类名通常包含 $Lambda$ 和数字
            // 例如：com.example.User$$Lambda$1/0x0000000800c0c000
            if (className.contains("$$Lambda")) {
                // 尝试通过反射获取更多信息
                try {
                    // 获取所有方法，寻找可能的字段访问方法
                    Method[] methods = column.getClass().getDeclaredMethods();
                    for (Method method : methods) {
                        String methodName = method.getName();
                        // 检查是否是 getter 方法（以 get 开头）
                        if (methodName.startsWith("get") && methodName.length() > 3) {
                            String fieldName = methodName.substring(3);
                            return fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
                        }
                    }
                } catch (Exception ignored) {
                    // 忽略反射异常，继续使用其他方法
                }

                // 如果无法通过方法名获取，尝试解析类名中的信息
                // 这需要根据具体的 Lambda 实现来调整
                String[] parts = className.split("\\$");
                if (parts.length > 1) {
                    // 尝试从类名中提取字段信息
                    // 这是一个简化的实现，实际使用时可能需要更复杂的逻辑
                    return extractFieldNameFromClassName(className);
                }
            }

            // 如果所有方法都失败，抛出异常
            throw new RuntimeException("无法从 Lambda 表达式中解析字段名: " + className);
        } catch (Exception e) {
            throw new RuntimeException("无法解析字段名", e);
        }
    }

    /**
     * 从类名中提取字段名
     *
     * @param className 类名
     * @return 字段名
     */
    private static String extractFieldNameFromClassName(String className) {
        try {
            // 这是一个简化的实现
            // 实际项目中，可能需要根据具体的 Lambda 实现来调整这个逻辑

            // 尝试从类名中查找常见的字段名模式
            // 这里提供一个基础的实现，实际使用时需要根据具体情况调整

            // 如果类名包含特定的模式，可以尝试解析
            if (className.contains("get")) {
                // 尝试提取 get 后面的字段名
                int getIndex = className.indexOf("get");
                if (getIndex >= 0 && getIndex + 3 < className.length()) {
                    String remaining = className.substring(getIndex + 3);
                    // 找到下一个大写字母或特殊字符的位置
                    int endIndex = 0;
                    for (int i = 1; i < remaining.length(); i++) {
                        if (Character.isUpperCase(remaining.charAt(i))) {
                            endIndex = i;
                            break;
                        }
                    }
                    if (endIndex > 0) {
                        String fieldName = remaining.substring(0, endIndex);
                        return fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
                    }
                }
            }

            // 如果无法解析，返回一个默认值
            return "unknownField";
        } catch (Exception e) {
            return "unknownField";
        }
    }
}
