package com.richie.contract.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * <p>类描述：执行结果实体类
 * <pre>
 * 改动说明：
 *      【修改人：王锦阳 / 2015年12月3日 下午2:51:55 / 版本：1.0】
 *      初始版本创建
 *      【修改人：王锦阳 / 2018年03月23日 上午11:17:55 / 版本：2.0】
 *      结构改进
 *      【修改人：王锦阳 / 2021年02月02日 上午09:32:01 / 版本：3.0】
 *      结构改进
 *      【修改人：王锦阳 / 2024年05月04日 下午02:02:01 / 版本：4.0】
 *      结构改进
 *      【修改人：王锦阳 / 2025年07月 / 版本：5.0】
 *      修复escapeJsonString未处理UTF-16代理项对的潜在Bug（emoji/罕见字符场景）
 *      优化convert()消除每次调用创建ObjectMapper的内存开销
 * </pre>
 *
 * @param <T> 执行结果的具体类型
 * @author richie696
 * @version 5.0
 * @since 2021年02月02日 上午09:32:01
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
public class ApiResult<T> implements Serializable {

    /**
     * 当前对象是否成功
     */
    private boolean success = true;
    /**
     * 结果数据
     */
    private T data;
    /**
     * 结果代码
     */
    private String code;
    /**
     * 信息
     */
    private String msg;

    /**
     * 主档数据国际化字典表（不是页面展示，是数据库中的主档数据国际化专用字段）
     */
    private Map<String, Map<String, String>> i18nDict;

    /**
     * 时间戳
     */
    private final long timestamp = System.currentTimeMillis();

    /**
     * 默认构造方法
     */
    public ApiResult() {
    }

    /**
     * 创建包含应答数据的构造函数
     *
     * @param data 应答数据报文
     */
    public ApiResult(T data) {
        this.data = data;
    }

    /**
     * 创建包含状态码和消息的构造函数
     *
     * @param code 状态码
     * @param msg  消息
     */
    public ApiResult(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 创建成功结果对象的方法
     *
     * @param <T> 返回结果集类型
     * @return 返回成功结果
     */
    public static <T> ApiResult<T> success() {
        return new ApiResult<>();
    }

    /**
     * 创建成功结果对象的方法
     *
     * @param data 返回的报文数据
     * @param <T>  返回结果集类型
     * @return 返回成功结果
     */
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(data);
    }

    /**
     * 创建成功结果对象的方法
     *
     * @param code    返回的编码
     * @param message 返回的信息
     * @param data    返回的报文数据
     * @param <T>     返回结果集类型
     * @return 返回成功结果
     */
    public static <T> ApiResult<T> success(String code, String message, T data) {
        ApiResult<T> result = new ApiResult<>(data);
        result.code = code;
        result.msg = message;
        return result;
    }

    /**
     * 创建成功结果对象的方法
     *
     * @param message 返回的信息
     * @param data    返回的报文数据
     * @param <T>     返回结果集类型
     * @return 返回成功结果
     */
    public static <T> ApiResult<T> success(String message, T data) {
        ApiResult<T> result = new ApiResult<>(data);
        result.msg = message;
        result.code = "200";
        return result;
    }

    /**
     * 创建错误结果对象的方法
     *
     * @param message 消息体
     * @param args    消息参数
     * @param <T>     返回结果集类型
     * @return 返回错误结果
     */
    public static <T> ApiResult<T> error(String message, Object... args) {
        ApiResult<T> result = new ApiResult<>();
        result.success = false;
        return result.setCode("500").setMsg(String.format(message, args));
    }

    /**
     * 创建错误结果对象的方法
     *
     * @param code    错误编码
     * @param message 消息体
     * @param args    消息参数
     * @param <T>     返回结果集类型
     * @return 返回错误结果
     */
    public static <T> ApiResult<T> error(String code, String message, Object... args) {
        ApiResult<T> result = new ApiResult<>();
        result.success = false;
        return result.setCode(code).setMsg(String.format(message, args));
    }

    /**
     * 创建错误结果对象的方法
     *
     * @param code    错误编码
     * @param message 消息体
     * @param args    消息参数
     * @param <T>     返回结果集类型
     * @return 返回错误结果
     */
    public static <T> ApiResult<T> error(T data, String code, String message, Object... args) {
        ApiResult<T> result = new ApiResult<>();
        result.success = false;
        return result.setData(data).setCode(code).setMsg(String.format(message, args));
    }

    /**
     * 将当前对象转换为新的对象
     *
     * @param result 新的对象
     * @param <E>    新的对象类型
     * @return 返回新的对象
     */
    public <E> ApiResult<E> convert(E result) {
        ApiResult<E> newResult = new ApiResult<>();
        newResult.msg = this.msg;
        newResult.code = this.code;
        newResult.data = result;
        return newResult;
    }

    /**
     * 将当前对象转换为JSON字节数组
     *
     * @return JSON字节数组
     */
    public byte[] toBytes() {
        String json = toJson();
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 将当前对象转换为JSON字符串
     *
     * @return JSON格式的字符串
     */
    public String toJson() {
        StringBuilder json = new StringBuilder("{");

        // 处理data字段
        json.append("\"success\":");
        appendJsonValue(json, this.success);

        // 处理data字段
        json.append("\"data\":");
        appendJsonValue(json, this.data);

        // 处理code字段
        json.append(",\"code\":");
        appendJsonValue(json, this.code);

        // 处理msg字段
        json.append(",\"msg\":");
        appendJsonValue(json, this.msg);

        // 处理i18nDict字段
        json.append(",\"i18nDict\":");
        if (this.i18nDict == null || this.i18nDict.isEmpty()) {
            json.append("{}");
        } else {
            json.append("{");
            boolean firstEntry = true;
            for (Map.Entry<String, Map<String, String>> entry : this.i18nDict.entrySet()) {
                if (!firstEntry) {
                    json.append(",");
                }
                firstEntry = false;

                // 添加键
                appendJsonValue(json, entry.getKey());
                json.append(":{");

                // 添加值（嵌套Map）
                boolean firstInner = true;
                for (Map.Entry<String, String> innerEntry : entry.getValue().entrySet()) {
                    if (!firstInner) {
                        json.append(",");
                    }
                    firstInner = false;

                    appendJsonValue(json, innerEntry.getKey());
                    json.append(":");
                    appendJsonValue(json, innerEntry.getValue());
                }
                json.append("}");
            }
            json.append("}");
        }

        json.append("}");
        return json.toString();
    }

    /**
     * 将值添加到JSON字符串中
     *
     * @param json  StringBuilder对象
     * @param value 要添加的值
     */
    private void appendJsonValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof String) {
            json.append("\"").append(escapeJsonString((String) value)).append("\"");
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else if (value instanceof Map) {
            appendJsonMap(json, (Map<?, ?>) value);
        } else if (value instanceof Collection<?>) {
            appendJsonArray(json, (Collection<?>) value);
        } else if (value.getClass().isArray()) {
            appendJsonArray(json, Arrays.asList((Object[]) value));
        } else {
            // 其他对象类型，使用toString()并进行转义处理
            json.append("\"").append(escapeJsonString(value.toString())).append("\"");
        }
    }

    /**
     * 添加Map类型值到JSON字符串
     */
    private void appendJsonMap(StringBuilder json, Map<?, ?> map) {
        json.append("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;

            appendJsonValue(json, entry.getKey().toString());
            json.append(":");
            appendJsonValue(json, entry.getValue());
        }
        json.append("}");
    }

    /**
     * 添加集合类型值到JSON字符串
     */
    private void appendJsonArray(StringBuilder json, Collection<?> collection) {
        json.append("[");
        boolean first = true;
        for (Object item : collection) {
            if (!first) {
                json.append(",");
            }
            first = false;
            appendJsonValue(json, item);
        }
        json.append("]");
    }

    /**
     * 转义JSON字符串中的特殊字符
     * <p>
     * 处理规则：
     * <ul>
     *     <li>JSON规范要求转义的双引号、反斜杠和控制字符</li>
     *     <li>UTF-16代理项对（surrogate pair）：合法配对直接输出，由UTF-8编码处理；
     *         孤立代理项转义为{@code \\uXXXX}，避免产生非法JSON</li>
     * </ul>
     *
     * @param input 输入字符串
     * @return 转义后的字符串
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }

        StringBuilder result = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        // 处理控制字符（ASCII 0x00-0x1F）
                        result.append(String.format("\\u%04x", (int) c));
                    } else if (Character.isHighSurrogate(c)) {
                        // UTF-16高位代理项（0xD800-0xDBFF）：检查是否有配对的低位代理项
                        if (i + 1 < input.length() && Character.isLowSurrogate(input.charAt(i + 1))) {
                            // 合法的代理项对（如emoji 🎉 U+1F389），直接输出两个char，
                            // 最终由UTF-8编码器正确处理为4字节序列
                            result.append(c);
                            result.append(input.charAt(++i));
                        } else {
                            // 孤立的高位代理项，转义为\\uXXXX避免产生非法JSON
                            result.append(String.format("\\u%04x", (int) c));
                        }
                    } else if (Character.isLowSurrogate(c)) {
                        // 孤立的低位代理项（没有前置高位代理项），转义处理
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
            }
        }
        return result.toString();
    }
}
