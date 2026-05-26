package com.richie.context.common.api;

import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Spring应用程序上下文持有类
 *
 * @author richie696
 * @version 1.0
 * @since 2021-12-06 09:57:04
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    /** 默认构造函数，供 Spring 实例化使用。 */
    public SpringContextHolder() {
    }

    /**
     * Spring应用程序上下文对象
     */
    @Getter
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext)
            throws BeansException {
        SpringContextHolder.applicationContext = applicationContext;
    }

    /**
     * 方法描述：根据Spring Bean ID获取Bean对象实例的方法
     *
     * @param beanId Spring Bean ID
     * @param <T>    实例类型
     * @return 返回Bean实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String beanId) {
        return (T) applicationContext.getBean(beanId);
    }

    /**
     * 方法描述：根据Spring Bean ID获取Bean对象实例的方法
     *
     * @param cls 要获取的实例类型
     * @param <T> 实例类型
     * @return 返回Bean实例
     */
    public static <T> T getBean(Class<T> cls) {
        return applicationContext.getBean(cls);
    }

    /**
     * 通过类上的注解获取类
     *
     * @param annotation anno
     * @return map
     */
    public static Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotation) {
        assertApplicationContext();
        return applicationContext.getBeansWithAnnotation(annotation);
    }

    private static void assertApplicationContext() {
        if (applicationContext == null) {
            throw new RuntimeException("application Context属性为null,请检查是否注入了Spring应用上下文对象！");
        }
    }

}
