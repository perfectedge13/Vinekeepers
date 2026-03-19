package com.vinekeepers.profile;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BindPlaceholderResolverTest {

    @Test
    void resolvesNestedMapPlaceholders() {
        Map<String, Object> lookup = Map.of("codeChange", "hello");
        Object out = BindPlaceholderResolver.resolveDeep(Map.of("feature_summary", "{{codeChange}}"), lookup);
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) out;
        assertEquals("hello", m.get("feature_summary"));
    }
}
