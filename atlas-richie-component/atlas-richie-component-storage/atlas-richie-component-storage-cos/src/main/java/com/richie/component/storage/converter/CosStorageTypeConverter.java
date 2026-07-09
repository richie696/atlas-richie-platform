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
package com.richie.component.storage.converter;

import com.qcloud.cos.model.StorageClass;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import com.richie.component.storage.exception.StorageTypeUnsupportedException;
import jakarta.annotation.Nonnull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.richie.component.storage.enums.StorageTypeEnum.*;

/**
 * 腾讯云COS存储类型转换器
 *
 * @author richie696
 * @version 1.0
 * @see <a href="https://cloud.tencent.com/document/product/436/33417">腾讯云存储桶类型</a>
 * @since 2025-06-03 11:32:04
 */
@Component
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "tencent_cos")
public class CosStorageTypeConverter implements StorageTypeConverter {

    @Override
    public String convertToEngineType(@Nonnull StorageTypeEnum storageType) {
        return switch (storageType) {
            case STANDARD -> StorageClass.Standard.toString();
            case STANDARD_IA -> StorageClass.Standard_IA.toString();
            case ARCHIVE, COLD_ARCHIVE -> StorageClass.Archive.toString();
            case DEEP_COLD_ARCHIVE -> StorageClass.Deep_Archive.toString();
            case INTELLIGENT_TIERING -> StorageClass.Intelligent_Tiering.toString();
            case MULTI_AZ_STANDARD -> StorageClass.Maz_Standard.toString();
            case MULTI_AZ_STANDARD_IA -> StorageClass.Maz_Standard_IA.toString();
            case MULTI_AZ_ARCHIVE, MULTI_AZ_COLD_ARCHIVE -> StorageClass.Maz_Archive.toString();
            case MULTI_AZ_DEEP_COLD_ARCHIVE -> StorageClass.Maz_Deep_Archive.toString();
            case MULTI_AZ_INTELLIGENT_TIERING -> StorageClass.Maz_Intelligent_Tiering.toString();
            default -> throw new StorageTypeUnsupportedException(getSupportedEngine(), storageType,
                    "仅支持: %s、%s、%s、%s、%s、%s、%s、%s、%s、%s、%s和%s存储类型。".formatted(STANDARD, STANDARD_IA, ARCHIVE, COLD_ARCHIVE,
                            DEEP_COLD_ARCHIVE, INTELLIGENT_TIERING, MULTI_AZ_STANDARD, MULTI_AZ_STANDARD_IA, MULTI_AZ_ARCHIVE
                            , MULTI_AZ_COLD_ARCHIVE, MULTI_AZ_DEEP_COLD_ARCHIVE, MULTI_AZ_INTELLIGENT_TIERING));
        };
    }

    @Override
    public StorageEngineEnum getSupportedEngine() {
        return StorageEngineEnum.TENCENT_COS;
    }
}
