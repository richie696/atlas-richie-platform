package com.richie.component.storage.local.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件元数据实体
 *
 * <p>与表 rd_file_metadata 对应。
 */
@Data
@Accessors(chain=true)
@TableName("rd_file_metadata")
public class FileMetadata implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String keyPath;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String hashValue;
    private String versionId;
    private String physicalPath;
    private LocalDateTime uploadTime;
    private LocalDateTime lastAccessTime;
    private Long accessCount;
    private String storageState;
    private LocalDateTime deletedTime;
}


