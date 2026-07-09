/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.statemachine;

/**
 * 状态机名称接口
 * <p>
 * 统一的状态机名称表示，支持字符串和枚举类型。
 * 用于在状态机引擎中统一处理不同类型的状态机名称。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
public interface StateMachineName {
    
    /**
     * 获取状态机名称
     * <p>
     * 返回状态机名称的字符串表示。
     * 
     *
     * @return 状态机名称（字符串）
     */
    String getStateMachineName();
    
    /**
     * 创建字符串状态机名称
     * <p>
     * 从字符串创建状态机名称包装对象。
     * 
     *
     * @param stateMachineName 状态机名称（字符串）
     * @return 状态机名称包装对象
     */
    static StateMachineName of(String stateMachineName) {
        return new StringStateMachineName(stateMachineName);
    }
    
    /**
     * 创建枚举状态机名称
     * <p>
     * 从枚举创建状态机名称包装对象，使用枚举的 name() 方法获取字符串表示。
     * 
     *
     * @param stateMachineName 枚举状态机名称
     * @return 状态机名称包装对象
     */
    static StateMachineName of(Enum<?> stateMachineName) {
        return new EnumStateMachineName(stateMachineName);
    }
    
    /**
     * 字符串状态机名称实现
     * <p>
     * 内部类，用于包装字符串类型的状态机名称。
     * 
     */
    class StringStateMachineName implements StateMachineName {
        private final String stateMachineName;
        
        /**
         * 构造函数
         *
         * @param stateMachineName 状态机名称（字符串）
         */
        public StringStateMachineName(String stateMachineName) {
            this.stateMachineName = stateMachineName;
        }
        
        @Override
        public String getStateMachineName() {
            return stateMachineName;
        }
        
        @Override
        public String toString() {
            return "StringStateMachineName{stateMachineName='" + stateMachineName + "'}";
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            StringStateMachineName that = (StringStateMachineName) obj;
            return stateMachineName.equals(that.stateMachineName);
        }
        
        @Override
        public int hashCode() {
            return stateMachineName.hashCode();
        }
    }
    
    /**
     * 枚举状态机名称实现
     * <p>
     * 内部类，用于包装枚举类型的状态机名称。
     * 
     */
    class EnumStateMachineName implements StateMachineName {
        /**
         * 枚举状态机名称
         */
        private final Enum<?> stateMachineName;
        
        /**
         * 构造函数
         *
         * @param stateMachineName 枚举状态机名称
         */
        public EnumStateMachineName(Enum<?> stateMachineName) {
            this.stateMachineName = stateMachineName;
        }
        
        @Override
        public String getStateMachineName() {
            return stateMachineName.name();
        }
        
        /**
         * 获取枚举状态机名称
         *
         * @return 枚举状态机名称
         */
        public Enum<?> getStateMachineNameEnum() {
            return stateMachineName;
        }
        
        @Override
        public String toString() {
            return "EnumStateMachineName{stateMachineName=" + stateMachineName + "}";
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            EnumStateMachineName that = (EnumStateMachineName) obj;
            return stateMachineName.equals(that.stateMachineName);
        }
        
        @Override
        public int hashCode() {
            return stateMachineName.hashCode();
        }
    }
} 
