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
package com.richie.component.statemachine.rule;

import com.richie.component.statemachine.config.properties.RulesEngineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExpressionConfigHolder 单元测试
 *
 * @author richie696
 */
class ExpressionConfigHolderTest {

    @BeforeEach
    void setUp() {
        ExpressionConfigHolder.setConfig(null);
    }

    @Test
    void testGetSlowThresholdMs_Default() {
        long threshold = ExpressionConfigHolder.getSlowThresholdMs();
        assertEquals(50L, threshold);
    }

    @Test
    void testGetSlowThresholdMs_WithConfig() {
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setSlowThresholdMs(100L);
        ExpressionConfigHolder.setConfig(config);
        
        assertEquals(100L, ExpressionConfigHolder.getSlowThresholdMs());
    }

    @Test
    void testIsSecurityCheckEnabled_Default() {
        // 默认应该启用安全检查
        assertTrue(ExpressionConfigHolder.isSecurityCheckEnabled());
    }

    @Test
    void testIsSecurityCheckEnabled_Disabled() {
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setEnableSecurityCheck(false);
        ExpressionConfigHolder.setConfig(config);
        
        assertFalse(ExpressionConfigHolder.isSecurityCheckEnabled());
    }

    @Test
    void testIsDetailedLogEnabled_Default() {
        assertFalse(ExpressionConfigHolder.isDetailedLogEnabled());
    }

    @Test
    void testIsDetailedLogEnabled_Enabled() {
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setEnableDetailedLog(true);
        ExpressionConfigHolder.setConfig(config);
        
        assertTrue(ExpressionConfigHolder.isDetailedLogEnabled());
    }

    @Test
    void testGetSecurityBlacklist_Default() {
        Set<String> blacklist = ExpressionConfigHolder.getSecurityBlacklist();
        
        assertNotNull(blacklist);
        assertTrue(blacklist.contains("java.lang.Runtime"));
        assertTrue(blacklist.contains("ProcessBuilder"));
        assertTrue(blacklist.contains("System.exit"));
        assertTrue(blacklist.contains("Class.forName"));
    }

    @Test
    void testGetSecurityBlacklist_WithCustomBlacklist() {
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setSecurityBlacklist("custom.dangerous,another.unsafe");
        ExpressionConfigHolder.setConfig(config);
        
        Set<String> blacklist = ExpressionConfigHolder.getSecurityBlacklist();
        
        assertTrue(blacklist.contains("custom.dangerous"));
        assertTrue(blacklist.contains("another.unsafe"));
        // 默认黑名单也应该存在
        assertTrue(blacklist.contains("java.lang.Runtime"));
    }

    @Test
    void testGetSecurityBlacklist_WithEmptyCustomBlacklist() {
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setSecurityBlacklist("");
        ExpressionConfigHolder.setConfig(config);
        
        Set<String> blacklist = ExpressionConfigHolder.getSecurityBlacklist();
        
        // 应该只有默认黑名单
        assertTrue(blacklist.contains("java.lang.Runtime"));
    }

    @Test
    void testGetSecurityBlacklist_WithWhitespaceCustomBlacklist() {
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setSecurityBlacklist("  , custom.dangerous , another.unsafe ,  ");
        ExpressionConfigHolder.setConfig(config);
        
        Set<String> blacklist = ExpressionConfigHolder.getSecurityBlacklist();
        
        assertTrue(blacklist.contains("custom.dangerous"));
        assertTrue(blacklist.contains("another.unsafe"));
    }
}

