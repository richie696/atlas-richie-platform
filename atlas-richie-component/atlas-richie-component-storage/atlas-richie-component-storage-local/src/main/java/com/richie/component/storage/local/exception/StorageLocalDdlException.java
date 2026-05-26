package com.richie.component.storage.local.exception;

/**
 * 本地存储DDL执行异常
 *
 * <p>用于封装在初始化/迁移过程中生成或执行 DDL 失败的场景，
 * 例如无权限执行、生成 SQL 失败等。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-10-14
 */
public class StorageLocalDdlException extends RuntimeException {

    public StorageLocalDdlException(String message) {
        super(message);
    }

    public StorageLocalDdlException(String message, Throwable cause) {
        super(message, cause);
    }
}


