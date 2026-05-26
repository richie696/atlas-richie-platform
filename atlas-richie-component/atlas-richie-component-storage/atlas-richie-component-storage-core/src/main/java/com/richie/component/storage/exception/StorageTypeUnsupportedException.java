package com.richie.component.storage.exception;

import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;

/**
 * 当前存储引擎不支持指定存储类型时抛出的运行时异常。
 *
 * @author richie696
 * @since 2023-09-06
 */
public class StorageTypeUnsupportedException extends RuntimeException {

    /**
     * @param engine       存储引擎
     * @param storageType  存储类型
     * @param message      错误信息
     */
    public StorageTypeUnsupportedException(StorageEngineEnum engine, StorageTypeEnum storageType, String message) {
        super("[%s]%s - %s".formatted(engine.getDescription(), storageType.getDescription(), message));
    }

    /**
     * @param engine       存储引擎
     * @param storageType  存储类型
     * @param message      错误信息
     * @param cause        原因
     */
    public StorageTypeUnsupportedException(StorageEngineEnum engine, StorageTypeEnum storageType, String message, Throwable cause) {
        super("[%s]%s - %s".formatted(engine.getDescription(), storageType.getDescription(), message), cause);
    }

    /**
     * @param engine       存储引擎
     * @param storageType  存储类型
     * @param cause        原因
     */
    public StorageTypeUnsupportedException(StorageEngineEnum engine, StorageTypeEnum storageType, Throwable cause) {
        super("[%s]%s - 不支持的类型".formatted(engine.getDescription(), storageType.getDescription()), cause);
    }
}
