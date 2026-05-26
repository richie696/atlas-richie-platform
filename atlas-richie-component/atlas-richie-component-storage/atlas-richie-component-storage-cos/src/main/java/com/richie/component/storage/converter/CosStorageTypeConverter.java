package com.richie.component.storage.converter;

import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import com.richie.component.storage.exception.StorageTypeUnsupportedException;
import com.qcloud.cos.model.StorageClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.Nonnull;
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
