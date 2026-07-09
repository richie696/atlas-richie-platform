/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.i18n.aspect;

import com.richie.contract.model.ApiResult;
import com.richie.component.i18n.annotation.I18nControl;
import com.richie.component.i18n.annotation.I18nDict;
import com.richie.component.i18n.config.I18nProperties;
import com.richie.component.i18n.handle.I18nHandle;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 国际化字典自动注入切面
 *
 * @author richie696
 * @version 1.0
 * @since 2023-11-05 00:32:50
 */
@Slf4j
@Aspect
@Order(100)
@RequiredArgsConstructor
public class I18nDictAspect {

    /** 国际化配置，用于判断是否仅对带 I18nControl 的 Controller 做字典注入 */
    private final I18nProperties i18nProperties;

    /**
     * 切点：所有 Controller 的 public 方法。
     */
    @Pointcut("execution(public * com.richie..*.controller..*Controller.*(..))")
    public void excludeService() {
    }

    /**
     * 环绕增强：对 Controller 返回的 ResultVO 中的字典字段进行国际化注入。
     *
     * @param joinPoint 切点
     * @return 原方法返回值（可能已设置 i18nDict）
     * @throws Throwable 原方法或注入过程抛出的异常
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Around("excludeService()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        if (Boolean.TRUE.equals(i18nProperties.getEnableI18nControl())) {
            // 获取切点的class
            var clazz = joinPoint.getTarget().getClass();
            // 判断是否有I18nControl注解
            var annotationPresent = clazz.isAnnotationPresent(I18nControl.class);
            if (!annotationPresent) {
                log.debug("没有I18nControl注解，不进行字典注入");
                return joinPoint.proceed();
            }
        }
        var step1 = System.currentTimeMillis();
        var result = joinPoint.proceed();
        var step2 = System.currentTimeMillis();
        log.info("获取执行结果耗时：{}ms", step2 - step1);
        if (!(result instanceof ApiResult apiResult)) {
            log.debug("返回结果不是ResultVO，不进行字典注入");
            return result;
        }
        var data = apiResult.getData();
        if (data == null || !apiResult.isSuccess()) {
            return result;
        }
        Map<String, Map<String, String>> i18nDictMap = new HashMap<>();
        // 如果是分页数据，需要对records进行注入
        if (data instanceof Page page) {
            var beforeInjectRecords = (List<Object>) page.getRecords();
            // 执行注入
            beforeInjectRecords.forEach(o -> injectDict(o, i18nDictMap));
        } else {
            // 否则对VO进行注入
            var o = apiResult.getData();
            injectDict(o, i18nDictMap);
        }
        apiResult.setI18nDict(i18nDictMap);
        var step3 = System.currentTimeMillis();
        log.info("注入字典耗时：{}ms", step3 - step2);
        return apiResult;
    }

    /**
     * 对单个结果对象中带 @I18nDict 的字段进行扫描，并将字典 key 收集到 i18nDictMap。
     *
     * @param result       控制器返回的数据（VO 或分页 records 中的元素）
     * @param i18nDictMap  用于收集字典 key -> locale -> 文案 的 Map
     */
    private void injectDict(Object result, Map<String, Map<String, String>> i18nDictMap) {
        if (result == null) {
            return;
        }
        Map<Object, Field[]> i18nFields;
        try {
            i18nFields = getI18nFields(result);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        if (i18nFields.isEmpty()) {
            log.debug("没有需要注入的字典");
            return;
        }
        for (var obj : i18nFields.keySet()) {
            var fields = i18nFields.get(obj);
            for (var field : fields) {
                try {
                    field.setAccessible(true);
                    var fieldValue = field.get(obj);
                    if (fieldValue == null) {
                        log.debug("类型 " + obj.getClass().getName() + " 的 " + field.getName() + " 字段有 null 值，扫描国际化内容将跳过。");
                        continue;
                    }
                    if (fieldValue instanceof String msgKey) {
                        if (StringUtils.isBlank(msgKey)) {
                            continue;
                        }
                        var i18nValues = I18nHandle.getI18nDictionaries(msgKey);
                        i18nDictMap.put(msgKey, i18nValues);
                    } else {
                        throw new IllegalArgumentException("注入字典失败，注解字段类型必须为String");
                    }
                } catch (IllegalAccessException e) {
                    log.error("注入字典失败", e);
                    throw new RuntimeException("注入字典失败");
                }
            }
        }
    }


    /**
     * 递归获取对象及其嵌套对象中所有带 @I18nDict 的字段（按对象分组）。
     *
     * @param result 根对象
     * @return 对象 -> 该对象上带 @I18nDict 的字段数组
     * @throws IllegalAccessException 反射访问异常
     */
    private static Map<Object, Field[]> getI18nFields(Object result) throws IllegalAccessException {
        if (result == null) {
            return Map.of();
        }
        var allFields = getAllFields(result.getClass());
        Map<Object, Field[]> i18nFieldsMap = new HashMap<>();
        if (Arrays.stream(allFields).noneMatch(field -> field.isAnnotationPresent(I18nDict.class))) {
            return i18nFieldsMap;
        }
        for (var field : allFields) {
            if (field.isAnnotationPresent(I18nDict.class)) {
                var fields = i18nFieldsMap.get(result);
                if (fields == null) {
                    fields = new Field[]{field};
                } else {
                    fields = Arrays.copyOf(fields, fields.length + 1);
                    fields[fields.length - 1] = field;
                }
                i18nFieldsMap.put(result, fields);
            } else if (field.getType() == List.class || field.getType() == Set.class) {
                field.setAccessible(true);
                var object = field.get(result);
                if (object instanceof List<?> list) {
                    for (var o : list) {
                        var i18nFields = getI18nFields(o);
                        i18nFieldsMap.putAll(i18nFields);
                    }
                } else if (object instanceof Set<?> set) {
                    for (var o : set) {
                        var i18nFields = getI18nFields(o);
                        i18nFieldsMap.putAll(i18nFields);
                    }
                }
            } else if (!ClassUtils.isPrimitiveOrWrapper(field.getType())) {
                field.setAccessible(true);
                var object = field.get(result);
                var i18nFields = getI18nFields(object);
                i18nFieldsMap.putAll(i18nFields);
            }
        }
        return i18nFieldsMap;
    }


    /**
     * 获取类及其父类的所有声明字段（含继承）。
     *
     * @param clazz 目标类
     * @return 字段数组
     */
    private static Field[] getAllFields(Class<?> clazz) {
        var fields = clazz.getDeclaredFields();
        var superclass = clazz.getSuperclass();
        if (superclass != null) {
            var superFields = getAllFields(superclass);
            var allFields = new Field[fields.length + superFields.length];
            System.arraycopy(fields, 0, allFields, 0, fields.length);
            System.arraycopy(superFields, 0, allFields, fields.length, superFields.length);
            return allFields;
        }
        return fields;
    }
}
