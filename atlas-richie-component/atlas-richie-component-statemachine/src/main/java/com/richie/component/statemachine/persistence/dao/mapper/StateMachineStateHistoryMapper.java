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
package com.richie.component.statemachine.persistence.dao.mapper;

import com.richie.component.statemachine.persistence.dao.entity.StateMachineStateHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 状态机历史记录 Mapper
 * <p>
 * MyBatis Plus Mapper 接口，用于操作 statemachine_state_history 表。
 * 提供基础的 CRUD 操作，支持历史记录的查询和批量插入。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
@Mapper
public interface StateMachineStateHistoryMapper extends BaseMapper<StateMachineStateHistory> {

}

