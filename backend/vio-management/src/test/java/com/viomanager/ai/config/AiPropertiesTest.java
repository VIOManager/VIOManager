package com.viomanager.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * AI 配置纯单测：compose 空串透传的回退口径（GLM_BASE_URL/GLM_MODEL 宿主未配置时
 * 容器拿到空字符串而非 unset，Spring 默认值语法不生效——getter 兜底）。
 */
class AiPropertiesTest {

    @Test
    void blankBaseUrlAndModelFallBackToDefaults() {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("");
        properties.setModel("   ");
        assertEquals(AiProperties.DEFAULT_BASE_URL, properties.getBaseUrl());
        assertEquals(AiProperties.DEFAULT_MODEL, properties.getModel());
    }

    @Test
    void configuredValuesKept() {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://localhost:11434/v1");
        properties.setModel("qwen2.5:7b");
        assertEquals("http://localhost:11434/v1", properties.getBaseUrl(), "本地 Ollama 应原样生效");
        assertEquals("qwen2.5:7b", properties.getModel());
    }

    @Test
    void enabledOnlyWithNonBlankKey() {
        AiProperties properties = new AiProperties();
        assertFalse(properties.isEnabled());
        properties.setApiKey("  ");
        assertFalse(properties.isEnabled(), "纯空白 key 也算未配置");
        properties.setApiKey("real-key");
        assertTrue(properties.isEnabled());
    }
}
