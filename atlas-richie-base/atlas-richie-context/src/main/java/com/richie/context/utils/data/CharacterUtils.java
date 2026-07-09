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
package com.richie.context.utils.data;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 字符集处理工具类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-15 17:49:05
 */
@Slf4j
@SuppressWarnings("unused")
public final class CharacterUtils {

    private static final int BUFFER_SIZE = 1024;

    private CharacterUtils() {
    }

    /**
     * 过滤GB2312内不支持的字符集（不含日韩俄字符）的方法
     *
     * @param origin      需要处理的原始字符串
     * @param placeholder 自定义占位符
     * @return 返回处理后的字符串
     */
    public static String filterContentForGB2312(String origin, String placeholder) {
        var builder = new StringBuilder();
        var originChars = origin.toCharArray();
        for (char originChar : originChars) {
            if (isSupportedCharacter(originChar)) {
                builder.append(originChar);
            } else {
                builder.append(placeholder);
            }
        }
        return builder.toString();
    }

    /**
     * 过滤GB2312内不支持的字符集（不含日韩俄字符）的方法
     *
     * @param origin 需要处理的原始字符串
     * @return 返回处理后的字符串
     */
    public static String filterContentForGB2312(String origin) {
        return filterContentForGB2312(origin, "");
    }

    /**
     * 检查指定字符是否在支持的字符范围内的方法
     *
     * @param c 待检查的字符
     * @return 返回检查结果（true：支持，false：不支持）
     */
    public static boolean isSupportedCharacter(char c) {
        return switch (Character.UnicodeScript.of(c)) {
            // 公共字符区
            case COMMON -> CharUtils.isAscii(c);
            // 英文字符区
            case LATIN -> UnicodeEnum.BASIC_LATIN.isCurrentRange(c);
            // 汉字区
            case HAN -> UnicodeEnum.CJK_UNIFIED_IDEOGRAPHS.isCurrentRange(c);
            default -> false;
        };
    }

    /**
     * 检查指定字符是否在支持的字符范围内的方法
     *
     * @param c       待检查的字符
     * @param scripts 待指定的字符集列表
     * @return 返回检查结果（true：支持，false：不支持）
     */
    public static boolean isSupportedCharacter(char c, Character.UnicodeScript... scripts) {
        Character.UnicodeScript charScript = Character.UnicodeScript.of(c);
        return Arrays.asList(scripts).contains(charScript);
    }

    /**
     * 检查指定字符是否在支持的字符范围内的方法
     *
     * @param c            待检查的字符
     * @param unicodeEnums 待指定的字符集列表
     * @return 返回检查结果（true：支持，false：不支持）
     */
    public static boolean isSupportedCharacter(char c, UnicodeEnum... unicodeEnums) {
        if (ArrayUtils.isEmpty(unicodeEnums)) {
            return isSupportedCharacter(c);
        }
        return Arrays.stream(unicodeEnums).anyMatch(o -> o.isCurrentRange(c));
    }

    /**
     * 执行文本压缩的方法
     *
     * @param originString 原始文本
     * @return 返回压缩后的文本
     */
    @Nullable
    public static String compress(@Nonnull String originString) {
        if (StringUtils.isBlank(originString)) {
            return originString;
        }
        try (var in = new ByteArrayInputStream(originString.getBytes(StandardCharsets.UTF_8));
             var out = new ByteArrayOutputStream();
             var gzip = new GZIPOutputStream(out)) {
            int count;
            var data = new byte[BUFFER_SIZE];
            while ((count = in.read(data, 0, BUFFER_SIZE)) != -1) {
                gzip.write(data, 0, count);
            }
            gzip.finish();
            gzip.flush();
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            log.error("文本内容压缩失败，错误原因：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 执行文本解压缩的方法
     *
     * @param compressedString 压缩文本
     * @return 返回解压缩后的文本
     */
    @Nullable
    public static String decompress(@Nonnull String compressedString) {
        if (StringUtils.isBlank(compressedString)) {
            return null;
        }
        var compressed = Base64.getDecoder().decode(compressedString);
        try (var out = new ByteArrayOutputStream();
             var in = new ByteArrayInputStream(compressed);
             var gzip = new GZIPInputStream(in)) {
            byte[] buffer = new byte[1024];
            int offset;
            while ((offset = gzip.read(buffer)) != -1) {
                out.write(buffer, 0, offset);
            }
            return out.toString();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * 全角转换成半角
     *
     * @param input 原始字符串
     * @return 返回转换后的字符串
     */
    public static String convertFull2Half(String input) {
        var c = input.toCharArray();
        for (var i = 0; i < c.length; i++) {
            if (c[i] == '\u3000') {
                c[i] = ' ';
            } else if (c[i] > '\uFF00' && c[i] < '｟') {
                c[i] = (char) (c[i] - 65248);
            }
        }
        return new String(c);
    }

    /**
     * 半角转全角
     *
     * @param input 原始字符串
     * @return 返回转换后的字符串
     */
    public static String convertHalf2Full(String input) {
        var c = input.toCharArray();
        for (var i = 0; i < c.length; i++) {
            if (c[i] == ' ') {
                c[i] = '\u3000';
            } else if (c[i] < '\177') {
                c[i] = (char) (c[i] + 65248);
            }
        }
        return new String(c);
    }

}
