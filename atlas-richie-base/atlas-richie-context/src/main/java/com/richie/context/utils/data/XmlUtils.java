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
package com.richie.context.utils.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.XmlWriteFeature;

import java.io.InputStream;

/**
 * <p>类定义：XML解析器工具类
 *
 * @author richie696
 * @version 1.0
 * @since 2021/12/7 19:04
 */
@Slf4j
public final class XmlUtils {

    private XmlUtils() {
    }

    /**
     * 创建XML解析器对象的方法
     *
     * @return 返回XML解析器
     */
    public static XmlMapper createXmlMapper() {
        // Jackson 3.0: XmlMapper 不可变，需要使用 builder 模式配置所有选项
        // Jackson 3.0: USE_STD_BEAN_NAMING 已被移除，标准 bean 命名约定现在总是启用
        return XmlMapper.builder()
                .configure(XmlWriteFeature.WRITE_XML_DECLARATION, true)
                .defaultUseWrapper(false)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .build();
    }

    /**
     * 将对象解析为XML数据结构的方法
     *
     * @param object 待解析的对象
     * @return 返回XML数据结构（如果解析失败，则返回null）
     */
    public static String serialize(Object object) {
        var xmlMapper = createXmlMapper();
        try {
            return xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JacksonException e) {
            log.error("{} 转换为XML格式失败，错误原因：{}", object.getClass().getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将XML数据结构解析为指定对象的方法
     *
     * @param inputStream 输入流
     * @param cls         指定的转换类型
     * @param <T>         解析返回的具体类型
     * @return 返回解析后的对象（如果解析失败，则返回null）
     */
    public static <T> T deserialize(InputStream inputStream, Class<T> cls) {
        XmlMapper xmlMapper = createXmlMapper();
        try {
            return xmlMapper.readValue(inputStream, cls);
        } catch (JacksonException e) {
            log.error("从流数据中转换 " + cls.getSimpleName() + " 对象失败，错误原因：" + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将XML数据结构解析为指定对象的方法
     *
     * @param xml xml文本
     * @param cls 指定的转换类型
     * @param <T> 解析返回的具体类型
     * @return 返回解析后的对象（如果解析失败，则返回null）
     */
    public static <T> T deserialize(String xml, Class<T> cls) {
        XmlMapper xmlMapper = createXmlMapper();
        try {
            return xmlMapper.readValue(xml, cls);
        } catch (JacksonException e) {
            log.error("从XML文本转换为 {} 对象失败，错误原因：{}", cls.getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

}
