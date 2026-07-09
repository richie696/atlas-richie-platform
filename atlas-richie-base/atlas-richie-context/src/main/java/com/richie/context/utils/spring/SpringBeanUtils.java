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
package com.richie.context.utils.spring;

import com.richie.context.common.api.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Map;

/**
 * Spring Bean 工具类
 * 提供从Spring容器中获取Bean的便捷方法，支持优先级选择（@Primary、@Order）
 *
 * @author richie696
 * @version 1.0
 * @since 2025-11-20
 */
@Slf4j
public class SpringBeanUtils {

    private SpringBeanUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 从Spring容器中获取指定类型的Bean
     * 如果存在多个实现，按优先级选择：@Primary > @Order > 默认（按Bean名称排序）
     * 使用 SpringContextHolder 中的 ApplicationContext
     *
     * @param interfaceClass 接口类型
     * @param <T> Bean类型
     * @return Bean实例，如果未找到则返回null
     */
    public static <T> T getBean(Class<T> interfaceClass) {
        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        if (applicationContext == null) {
            log.warn("ApplicationContext未初始化，无法获取Bean: {}", interfaceClass.getName());
            return null;
        }
        return getBean(applicationContext, interfaceClass);
    }

    /**
     * 从Spring容器中获取指定类型的Bean
     * 如果存在多个实现，按优先级选择：@Primary > @Order > 默认（按Bean名称排序）
     *
     * @param applicationContext Spring应用上下文
     * @param interfaceClass 接口类型
     * @param <T> Bean类型
     * @return Bean实例，如果未找到则返回null
     */
    public static <T> T getBean(ApplicationContext applicationContext, Class<T> interfaceClass) {
        if (applicationContext == null) {
            log.warn("ApplicationContext为null，无法获取Bean: {}", interfaceClass.getName());
            return null;
        }
        try {
            Map<String, T> beans = applicationContext.getBeansOfType(interfaceClass);
            if (beans.isEmpty()) {
                return null;
            }
            if (beans.size() == 1) {
                return beans.values().iterator().next();
            }
            // 多个实现，按优先级选择
            // 1. 优先选择 @Primary 标记的
            for (Map.Entry<String, T> entry : beans.entrySet()) {
                if (isPrimary(applicationContext, entry.getKey(), entry.getValue())) {
                    return entry.getValue();
                }
            }
            // 2. 如果没有 @Primary，按 @Order 排序，选择 Order 值最小的
            return beans.entrySet().stream()
                    .min((e1, e2) -> {
                        int order1 = getOrder(applicationContext, e1.getKey());
                        int order2 = getOrder(applicationContext, e2.getKey());
                        return Integer.compare(order1, order2);
                    })
                    .map(Map.Entry::getValue)
                    .orElse(beans.values().iterator().next());
        } catch (Exception e) {
            log.debug("获取Bean时发生异常: {}, 类型: {}", e.getMessage(), interfaceClass.getName());
            return null;
        }
    }

    /**
     * 检查Bean是否为Primary
     *
     * @param applicationContext Spring应用上下文
     * @param beanName Bean名称
     * @param bean Bean实例
     * @return 是否为Primary
     */
    private static boolean isPrimary(ApplicationContext applicationContext, String beanName, Object bean) {
        try {
            // 1. 先检查BeanDefinition中的isPrimary标志（如果ApplicationContext支持）
            if (applicationContext instanceof ConfigurableApplicationContext configurableContext) {
                try {
                    var beanDefinition = configurableContext.getBeanFactory().getBeanDefinition(beanName);
                    if (beanDefinition.isPrimary()) {
                        return true;
                    }
                } catch (Exception e) {
                    // 忽略异常，继续检查注解
                }
            }
            // 2. 检查Bean类上的@Primary注解
            Class<?> beanClass = getBeanClass(applicationContext, beanName, bean);
            if (beanClass != null && beanClass.isAnnotationPresent(Primary.class)) {
                return true;
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return false;
    }

    /**
     * 获取Bean的Order值
     *
     * @param applicationContext Spring应用上下文
     * @param beanName Bean名称
     * @return Order值，如果未找到则返回LOWEST_PRECEDENCE
     */
    private static int getOrder(ApplicationContext applicationContext, String beanName) {
        try {
            Class<?> beanClass = getBeanClass(applicationContext, beanName, null);
            if (beanClass != null) {
                Order order = beanClass.getAnnotation(Order.class);
                return order != null ? order.value() : Ordered.LOWEST_PRECEDENCE;
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * 获取Bean的实际类（处理代理类）
     *
     * @param applicationContext Spring应用上下文
     * @param beanName Bean名称
     * @param bean Bean实例
     * @return Bean的实际类
     */
    private static Class<?> getBeanClass(ApplicationContext applicationContext, String beanName, Object bean) {
        try {
            Class<?> beanClass = applicationContext.getType(beanName);
            if (beanClass == null && bean != null) {
                beanClass = bean.getClass();
            }
            if (beanClass != null) {
                // 如果是CGLIB代理，获取原始类
                while (beanClass.getName().contains("$$")) {
                    beanClass = beanClass.getSuperclass();
                }
            }
            return beanClass;
        } catch (Exception e) {
            return null;
        }
    }
}

