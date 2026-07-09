/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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


