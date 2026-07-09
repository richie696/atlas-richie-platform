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
package com.richie.component.storage.local.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.richie.component.storage.local.repository.entity.FileMetadata;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件元数据 Mapper（MyBatis-Plus）
 */
@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {
}


