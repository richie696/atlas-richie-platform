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
package com.richie.component.statemachine;

/**
 * 状态机事件接口
 * <p>
 * 统一的状态机事件表示，支持字符串和枚举类型。
 * 用于在状态机引擎中统一处理不同类型的事件。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
public interface StateMachineEvent {
    
    /**
     * 获取事件名称
     * <p>
     * 返回事件名称的字符串表示。
     * 
     *
     * @return 事件名称（字符串）
     */
    String getEventName();
    
    /**
     * 创建字符串事件
     * <p>
     * 从字符串创建事件包装对象。
     * 
     *
     * @param eventName 事件名称（字符串）
     * @return 事件包装对象
     */
    static StateMachineEvent of(String eventName) {
        return new StringEvent(eventName);
    }
    
    /**
     * 创建枚举事件
     * <p>
     * 从枚举创建事件包装对象，使用枚举的 name() 方法获取字符串表示。
     * 
     *
     * @param event 枚举事件
     * @return 事件包装对象
     */
    static StateMachineEvent of(Enum<?> event) {
        return new EnumEvent(event);
    }
    
    /**
     * 字符串事件实现
     * <p>
     * 内部类，用于包装字符串类型的事件。
     * 
     */
    class StringEvent implements StateMachineEvent {
        private final String eventName;
        
        /**
         * 构造函数
         *
         * @param eventName 事件名称（字符串）
         */
        public StringEvent(String eventName) {
            this.eventName = eventName;
        }
        
        @Override
        public String getEventName() {
            return eventName;
        }
        
        @Override
        public String toString() {
            return "StringEvent{eventName='" + eventName + "'}";
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            StringEvent that = (StringEvent) obj;
            return eventName.equals(that.eventName);
        }
        
        @Override
        public int hashCode() {
            return eventName.hashCode();
        }
    }
    
    /**
     * 枚举事件实现
     * <p>
     * 内部类，用于包装枚举类型的事件。
     * 
     */
    class EnumEvent implements StateMachineEvent {
        /**
         * 枚举事件
         */
        private final Enum<?> event;
        
        /**
         * 构造函数
         *
         * @param event 枚举事件
         */
        public EnumEvent(Enum<?> event) {
            this.event = event;
        }
        
        @Override
        public String getEventName() {
            return event.name();
        }
        
        /**
         * 获取枚举事件
         *
         * @return 枚举事件
         */
        public Enum<?> getEvent() {
            return event;
        }
        
        @Override
        public String toString() {
            return "EnumEvent{event=" + event + "}";
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            EnumEvent that = (EnumEvent) obj;
            return event.equals(that.event);
        }
        
        @Override
        public int hashCode() {
            return event.hashCode();
        }
    }
} 
