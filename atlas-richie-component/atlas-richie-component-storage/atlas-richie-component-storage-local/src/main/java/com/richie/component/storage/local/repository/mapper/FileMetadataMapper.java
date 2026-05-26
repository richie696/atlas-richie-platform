package com.richie.component.storage.local.repository.mapper;

import com.richie.component.storage.local.repository.entity.FileMetadata;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件元数据 Mapper（MyBatis-Plus）
 */
@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {
}


