package com.richie.component.storage.converter;

import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import jakarta.annotation.Nonnull;

public interface StorageTypeConverter {

    /**
     * 将存储类型转换为引擎类型
     *
     * @param storageType 存储类型枚举
     * @return 返回对应的存储引擎类型字符串
     */
    String convertToEngineType(@Nonnull StorageTypeEnum storageType);

    /**
     * 获取支持的存储引擎类型
     *
     * @return 支持的存储引擎枚举
     */
    StorageEngineEnum getSupportedEngine();

}
