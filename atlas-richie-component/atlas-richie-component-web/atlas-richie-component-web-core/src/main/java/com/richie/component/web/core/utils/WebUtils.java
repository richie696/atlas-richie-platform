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
package com.richie.component.web.core.utils;

import com.richie.context.utils.data.JsonUtils;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.util.StdDateFormat;
import tools.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;

import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Web 层工具类：HTTP 消息转换器刷新、日期格式等。
 *
 * @author richie696
 * @since 2022-10-09
 */
public class WebUtils {

    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private static final SimpleDateFormat CUSTOM_FORMAT = new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS) {
        @Override
        public Date parse(String source) {
            try {
                return super.parse(source);
            } catch (Exception e) {
                try {
                    return StdDateFormat.instance.parse(source);
                } catch (ParseException e1) {
                    throw new RuntimeException("Invalid date format: " + e.getMessage());
                }
            }
        }
    };

    private WebUtils() {
    }

    /**
     * 刷新 HTTP 消息转换器（Spring Boot 4.x 新 API）：通过 ServerBuilder 注册使用平台 ObjectMapper 的 JSON 转换器。
     *
     * @param builder HTTP 消息转换器构建器
     */
    public static void refreshHttpMessageConverter(HttpMessageConverters.ServerBuilder builder) {
        JacksonJsonHttpMessageConverter converter = createPlatformJacksonJsonConverter();
        builder.withJsonConverter(converter);
    }

    /**
     * 创建使用平台 JsonMapper（Long/日期/时区/可选 XML 模块）的 JacksonJsonHttpMessageConverter。
     *
     * @return 配置好的 JSON 消息转换器
     */
    private static JacksonJsonHttpMessageConverter createPlatformJacksonJsonConverter() {
        JavaTimeModule timeModule = new JavaTimeModule();
        SimpleModule customModule = new SimpleModule();
        JacksonModule xmlModuleTemp = null;
        try {
            Class<?> xmlModuleClass = Class.forName("tools.jackson.dataformat.xml.JacksonXmlModule");
            xmlModuleTemp = (JacksonModule) xmlModuleClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // 如果 jackson-dataformat-xml 不在 classpath 中，跳过 XML 模块
        }
        final JacksonModule xmlModule = xmlModuleTemp;

        customModule.addSerializer(Long.class, ToStringSerializer.instance);
        customModule.addSerializer(BigInteger.class, ToStringSerializer.instance);
        customModule.addSerializer(Date.class, new StdSerializer<Date>(Date.class) {
            @Override
            public void serialize(Date value, JsonGenerator gen, SerializationContext ctx) {
                gen.writeString(CUSTOM_FORMAT.format(value));
            }
        });
        customModule.addDeserializer(Date.class, new StdDeserializer<Date>(Date.class) {
            @Override
            public Date deserialize(JsonParser p, DeserializationContext ctxt) {
                String dateStr = p.getValueAsString();
                try {
                    return CUSTOM_FORMAT.parse(dateStr);
                } catch (ParseException e) {
                    try {
                        return StdDateFormat.instance.parse(dateStr);
                    } catch (ParseException e1) {
                        throw new RuntimeException("Invalid date format: " + dateStr, e1);
                    }
                }
            }
        });

        var baseMapperBuilder = JsonUtils.getInstance().cloneMapper()
                .defaultTimeZone(LocaleContextHolder.getTimeZone())
                .addModule(timeModule)
                .addModule(customModule);
        if (xmlModule != null) {
            baseMapperBuilder.addModule(xmlModule);
        }
        ObjectMapper customMapper = baseMapperBuilder.build();
        if (!(customMapper instanceof JsonMapper customJsonMapper)) {
            throw new IllegalStateException("JsonUtils.cloneMapper().build() should return JsonMapper instance, but got: " + customMapper.getClass());
        }
        return new JacksonJsonHttpMessageConverter(customJsonMapper);
    }
}
