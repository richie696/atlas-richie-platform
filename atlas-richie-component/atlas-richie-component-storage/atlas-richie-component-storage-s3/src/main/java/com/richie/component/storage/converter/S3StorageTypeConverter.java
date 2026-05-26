package com.richie.component.storage.converter;

import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import com.richie.component.storage.exception.StorageTypeUnsupportedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.StorageClass;

import static com.richie.component.storage.enums.StorageTypeEnum.*;

/**
 * Amazon S3存储类型转换器
 *
 * @author richie696
 * @version 1.0
 * @see <a href="https://aws.amazon.com/cn/s3/storage-classes/?nc=sn&loc=3">Amazon S3存储桶类型</a>
 * @since 2025-06-03 11:32:04
 */
@Component
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "aws_s3")
public class S3StorageTypeConverter implements StorageTypeConverter {

    @Override
    public String convertToEngineType(@Nonnull StorageTypeEnum storageType) {
        return switch (storageType) {
            case STANDARD -> StorageClass.STANDARD.toString();
            case STANDARD_IA -> StorageClass.STANDARD_IA.toString();
            case ONEZONE_IA -> StorageClass.ONEZONE_IA.toString();
            case SNOW -> StorageClass.SNOW.toString();
            case GLACIER -> StorageClass.GLACIER.toString();
            case GLACIER_IR -> StorageClass.GLACIER_IR.toString();
            case Outposts -> StorageClass.OUTPOSTS.toString();
            case REDUCED_REDUNDANCY -> StorageClass.REDUCED_REDUNDANCY.toString();
            case DEEP_COLD_ARCHIVE -> StorageClass.DEEP_ARCHIVE.toString();
            case INTELLIGENT_TIERING -> StorageClass.INTELLIGENT_TIERING.toString();
            default -> throw new StorageTypeUnsupportedException(getSupportedEngine(), storageType,
                    "仅支持: %s、%s、%s、%s、%s、%s、%s、%s、%s和%s存储类型。".formatted(STANDARD, STANDARD_IA,
                            ONEZONE_IA, SNOW, GLACIER, GLACIER_IR, Outposts, REDUCED_REDUNDANCY,
                            DEEP_COLD_ARCHIVE, INTELLIGENT_TIERING));
        };
    }

    @Override
    public StorageEngineEnum getSupportedEngine() {
        return StorageEngineEnum.AWS_S3;
    }
}
