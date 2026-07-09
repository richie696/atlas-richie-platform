/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
