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
package com.richie.context.utils.spring;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 通用工具类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-10-13 00:29:55
 */
@Slf4j
public class CommonUtils {

    private CommonUtils() {
    }

    /**
     * 复制对象属性的方法
     *
     * @param source 源对象
     * @param target 目标对象
     * @param ignoreNullValue 是否忽略空值（true：是，false：不是）
     */
    public static void copyProperties(Object source, Object target, boolean ignoreNullValue) {
        if (ignoreNullValue) {
            BeanUtils.copyProperties(source, target, getNullPropertyNames(source));
        } else {
            BeanUtils.copyProperties(source, target);
        }
    }

    private static String[] getNullPropertyNames (Object source) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        PropertyDescriptor[] pds = src.getPropertyDescriptors();

        Set<String> emptyNames = new HashSet<>();
        for(PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null) {
                emptyNames.add(pd.getName());
            }
        }
        String[] result = new String[emptyNames.size()];
        return emptyNames.toArray(result);
    }

    /**
     * 获取当前进程ID的方法
     *
     * @return 返回当前进程ID
     */
    public static String getPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        log.debug("Name Of Runtime MX Bean is:{}", name);
        return name.split("@")[0];
    }

    /**
     * <p>字符串快速分割。
     *
     * <p>
     * 提供更高效简洁的分割算法，在相同情况下，分割效率约为<br>
     * {@link String#split(String)}的4倍<br>
     * 但仅支持单个字符作为分割符。
     *
     * <p>
     * 当待分割字符串为null或者空串时，返回长度为0的数组<br>
     * 该方法不会抛出任何异常
     *
     * @param source    待分割字符串
     * @param splitChar 分隔符
     * @return 分割完毕的字符串数组
     */
    public static String[] split(final String source, final char splitChar) {

        String[] strArr;
        List<String> strList = new LinkedList<>();
        if (null == source || source.isEmpty()) {
            strArr = new String[0];
        } else {
            char[] charArr = source.toCharArray();
            int start = 0;
            int end = 0;
            while (end < source.length()) {
                char c = charArr[end];
                if (c == splitChar) {
                    if (start != end) {
                        String fragment = source.substring(start, end);
                        strList.add(fragment);
                    }
                    start = end + 1;
                }
                ++end;
            }
            if (start < source.length()) {
                strList.add(source.substring(start));
            }

            strArr = new String[strList.size()];
            strList.toArray(strArr);
        }

        return strArr;
    }


    /**
     * <p>字符串快速分割。
     *
     * <p>
     * 提供更高效简洁的分割算法，在相同情况下，分割效率约为<br>
     * {@link String#split(String)}的4倍<br>
     * 支持字符串作为分割符。
     *
     * <p>
     * 当待分割字符串为null或者空串时，返回长度为0的数组<br>
     * 该方法不会抛出任何异常<br>
     * 如果传入的字符串中仅含有1个字符，那么该方法会被转交给{@link #split(String, char)}处理
     *
     * @param source   待分割字符串
     * @param splitStr 分隔符字符串
     * @return 分割完毕的字符串数组
     * @see #split(String, char)
     */
    public static String[] split(final String source, final String splitStr) {

        String[] strArr;
        if (null == source || source.isEmpty()) {
            strArr = new String[0];
        } else {
            if (splitStr.length() == 1) {
                strArr = split(source, splitStr.charAt(0));
            } else {
                int strLen = source.length();
                int splitStrLen = splitStr.length();
                List<String> strList = new LinkedList<>();
                int start = 0;
                int end;
                while (start < strLen) {
                    end = source.indexOf(splitStr, start);
                    if (end == -1) {
                        String fregment = source.substring(start);
                        strList.add(fregment);
                        break;
                    }
                    if (start != end) {
                        String fregment = source.substring(start, end);
                        strList.add(fregment);
                    }
                    start = end + splitStrLen;
                }

                strArr = new String[strList.size()];
                strList.toArray(strArr);
            }
        }

        return strArr;
    }

}
