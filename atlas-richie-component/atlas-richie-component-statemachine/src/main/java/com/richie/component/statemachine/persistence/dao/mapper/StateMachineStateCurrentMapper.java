package com.richie.component.statemachine.persistence.dao.mapper;

import com.richie.component.statemachine.persistence.dao.entity.StateMachineStateCurrent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 状态机当前状态 Mapper
 * <p>
 * MyBatis Plus Mapper 接口，用于操作 statemachine_state_current 表。
 * 提供基础的 CRUD 操作，支持复合主键查询。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
@Mapper
public interface StateMachineStateCurrentMapper extends BaseMapper<StateMachineStateCurrent> {

    /**
     * 批量插入或更新当前状态（MySQL）
     * <p>
     * 使用 MySQL 的 {@code INSERT ... ON DUPLICATE KEY UPDATE} 语法，
     * 当复合主键 (state_machine, business_id) 冲突时自动更新。
     * </p>
     *
     * @param list 当前状态列表
     */
    @Insert("<script>" +
            "INSERT INTO statemachine_state_current (state_machine, business_id, current_state, seq, updated_at) " +
            "VALUES " +
            "<foreach collection='list' item='item' separator=','> " +
            "(#{item.stateMachine}, #{item.businessId}, #{item.currentState}, #{item.seq}, #{item.updatedAt}) " +
            "</foreach> " +
            "ON DUPLICATE KEY UPDATE " +
            "current_state = VALUES(current_state), " +
            "seq = VALUES(seq), " +
            "updated_at = VALUES(updated_at)" +
            "</script>")
    void insertOrUpdateBatchForMysql(@Param("list") List<StateMachineStateCurrent> list);

    /**
     * 批量插入或更新当前状态（PostgreSQL）
     * <p>
     * 使用 PostgreSQL 的 {@code INSERT ... ON CONFLICT ... DO UPDATE} 语法，
     * 当复合主键 (state_machine, business_id) 冲突时自动更新。
     * </p>
     *
     * @param list 当前状态列表
     */
    @Insert("<script>" +
            "INSERT INTO statemachine_state_current (state_machine, business_id, current_state, seq, updated_at) " +
            "VALUES " +
            "<foreach collection='list' item='item' separator=','> " +
            "(#{item.stateMachine}, #{item.businessId}, #{item.currentState}, #{item.seq}, #{item.updatedAt}) " +
            "</foreach> " +
            "ON CONFLICT (state_machine, business_id) DO UPDATE SET " +
            "current_state = EXCLUDED.current_state, " +
            "seq = EXCLUDED.seq, " +
            "updated_at = EXCLUDED.updated_at" +
            "</script>")
    void insertOrUpdateBatchForPostgresql(@Param("list") List<StateMachineStateCurrent> list);

    /**
     * 批量插入或更新当前状态（Oracle）
     * <p>
     * 使用 Oracle 的 {@code MERGE INTO} 语法，
     * 当复合主键 (state_machine, business_id) 匹配时更新，否则插入。
     * </p>
     *
     * @param list 当前状态列表
     */
    @Insert("<script>" +
            "<foreach collection='list' item='item' separator=';'> " +
            "MERGE INTO statemachine_state_current t " +
            "USING (SELECT #{item.stateMachine} AS state_machine, #{item.businessId} AS business_id FROM DUAL) s " +
            "ON (t.state_machine = s.state_machine AND t.business_id = s.business_id) " +
            "WHEN MATCHED THEN " +
            "  UPDATE SET t.current_state = #{item.currentState}, t.seq = #{item.seq}, t.updated_at = #{item.updatedAt} " +
            "WHEN NOT MATCHED THEN " +
            "  INSERT (state_machine, business_id, current_state, seq, updated_at) " +
            "  VALUES (#{item.stateMachine}, #{item.businessId}, #{item.currentState}, #{item.seq}, #{item.updatedAt})" +
            "</foreach>" +
            "</script>")
    void insertOrUpdateBatchForOracle(@Param("list") List<StateMachineStateCurrent> list);
}

