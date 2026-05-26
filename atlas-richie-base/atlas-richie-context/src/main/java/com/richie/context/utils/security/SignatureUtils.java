package com.richie.context.utils.security;

import com.richie.context.utils.data.Collections;
import com.richie.context.utils.data.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

import java.util.Map;

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

    private static Map<String, Object> mapDeleteNull(String jsonString) {
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
        String origin = url + "?" + sortedParamStr + secret;
        String sig = HashUtils.md5(origin);
        log.info("================generateSig Begin====================");
        log.info("|{}|", origin);
        log.info("|{}|", sig);
        log.info("================generateSig End======================");
        return sig;
    }

}
