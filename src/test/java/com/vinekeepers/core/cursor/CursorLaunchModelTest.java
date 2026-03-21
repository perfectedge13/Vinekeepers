package com.vinekeepers.core.cursor;

import com.vinekeepers.bot.ModelProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CursorLaunchModelTest {

    @AfterEach
    void clearCursorModelProperty() {
        System.clearProperty("CURSOR_MODEL");
    }

    @Test
    void fromBotProfileReturnsNullForNull() {
        assertNull(CursorLaunchModel.fromBotProfile(null));
    }

    @Test
    void fromBotProfileReturnsNullForStubPlaceholder() {
        assertNull(CursorLaunchModel.fromBotProfile(new ModelProfile("stub", "stub")));
    }

    @Test
    void fromBotProfileReturnsTrimmedModelIdWhenPresent() {
        assertEquals("gpt-4o", CursorLaunchModel.fromBotProfile(new ModelProfile("openai", "gpt-4o")));
    }

    @Test
    void fromBotProfileReturnsNullWhenModelIdBlank() {
        assertNull(CursorLaunchModel.fromBotProfile(new ModelProfile("openai", "   ")));
    }

    @Test
    void resolvePrefersCursorModelOverModelKey() {
        Map<String, Object> args = new HashMap<>();
        args.put("model", "from-model-key");
        args.put("cursorModel", "from-cursor-key");
        assertEquals("from-cursor-key", CursorLaunchModel.resolveForLaunch(args));
    }

    @Test
    void resolveUsesModelWhenCursorModelBlank() {
        Map<String, Object> args = Map.of("model", "m-only", "cursorModel", "  ");
        assertEquals("m-only", CursorLaunchModel.resolveForLaunch(args));
    }

    @Test
    void resolveUsesBotDefaultWhenNoExplicitModel() {
        Map<String, Object> args = new HashMap<>();
        args.put("__botDefaultCursorModel", "bot-default");
        assertEquals("bot-default", CursorLaunchModel.resolveForLaunch(args));
    }

    @Test
    void resolveExplicitBeatsBotDefault() {
        Map<String, Object> args = new HashMap<>();
        args.put("cursorModel", "explicit");
        args.put("__botDefaultCursorModel", "bot-default");
        assertEquals("explicit", CursorLaunchModel.resolveForLaunch(args));
    }

    @Test
    void resolveUsesCursorModelEnvWhenNothingElse() {
        System.setProperty("CURSOR_MODEL", "env-model");
        assertEquals("env-model", CursorLaunchModel.resolveForLaunch(Map.of()));
    }

    @Test
    void resolveExplicitBeatsEnv() {
        System.setProperty("CURSOR_MODEL", "env-model");
        Map<String, Object> args = Map.of("model", "picked");
        assertEquals("picked", CursorLaunchModel.resolveForLaunch(args));
    }

    @Test
    void resolveBotDefaultBeatsEnv() {
        System.setProperty("CURSOR_MODEL", "env-model");
        Map<String, Object> args = Map.of("__botDefaultCursorModel", "bot");
        assertEquals("bot", CursorLaunchModel.resolveForLaunch(args));
    }
}
