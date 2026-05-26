package com.richie.component.dao.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * DAO 配置持有者，用于在非 Spring 注入场景（如 p6spy 日志）中获取 DaoProperties。
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-24
 */
@Component
public class DaoPropertiesHolder {

    private static final AtomicReference<DaoProperties> MANAGE = new AtomicReference<>();

    private DaoPropertiesHolder() {
    }

    public static DaoProperties getProperties() {
        var properties = MANAGE.get();
        if (properties == null) {
            properties = new DaoProperties();
            MANAGE.set(properties);
        }
        return properties;
    }

    @Autowired
    public void setProperties(DaoProperties daoProperties) {
        if (DaoPropertiesHolder.MANAGE.get() == null) {
            synchronized (DaoPropertiesHolder.class) {
                if (DaoPropertiesHolder.MANAGE.get() == null) {
                    DaoPropertiesHolder.MANAGE.set(daoProperties);
                }
            }
        }
    }
}
