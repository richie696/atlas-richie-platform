package com.richie.component.storage.core.impl;

import com.richie.context.common.api.SpringContextHolder;
import com.richie.component.storage.core.StorageEngine;

/**
 * 存储引擎抽象基类，提供客户端获取与销毁的通用逻辑。
 *
 * @param <T> 底层客户端类型（如 Ftp、S3Client 等）
 * @author richie696
 * @since 2023-09-05
 */
public abstract class AbstractDestroyEngine<T> implements StorageEngine {

    /**
     * 从 Spring 容器获取指定类型的存储客户端 Bean。
     *
     * @param clientClass 客户端类
     * @return 客户端实例
     */
    protected T getClient(Class<T> clientClass) {
        return SpringContextHolder.getBean(clientClass);
    }

    /**
     * 销毁/关闭客户端（子类可覆盖以释放连接等资源）。
     *
     * @param t 客户端实例
     */
    void destroy(T t) {
    }

}
