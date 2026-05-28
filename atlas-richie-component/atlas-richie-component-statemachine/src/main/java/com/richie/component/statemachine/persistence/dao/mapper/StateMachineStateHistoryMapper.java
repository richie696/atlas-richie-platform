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

