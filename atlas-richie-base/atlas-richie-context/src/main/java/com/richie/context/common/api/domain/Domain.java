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
package com.richie.context.common.api.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 领域模型根基类
 *
 * <p>所有实体类的公共基类，提供唯一主键 {@code id}。
 * 使用 MyBatis-Plus 雪花算法分布式 ID 生成策略 ({@link IdType#ASSIGN_ID})。</p>
 *
 * <p><b>继承体系</b>：
 * <pre>{@code
 * Domain (id)
 *   ├── AuditDomain (id + 审计字段 + 逻辑删除)
 *   │     ├── TenantAuditDomain (id + 审计 + 逻辑删除 + 租户)
 *   ├── TenantDomain (id + 租户)
 *   └── VersionDomain (id + 乐观锁)
 * }</pre>
 *
 * @author richie696
 * @since 1.0
 */
@Data
public abstract class Domain implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（分布式雪花算法 ID）
     */
    @TableId(type = IdType.ASSIGN_ID)
    protected Long id;

}
