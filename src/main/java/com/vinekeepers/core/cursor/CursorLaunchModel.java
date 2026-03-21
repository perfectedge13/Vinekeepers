package com.vinekeepers.core.cursor;

import com.vinekeepers.bot.ModelProfile;
import com.vinekeepers.env.Env;

import java.util.Map;

/**
 * Resolves the Cursor Cloud agent model string for a launch: explicit bind/state, bot default, then env.
 */
public final class CursorLaunchModel {

    private CursorLaunchModel() {
    }

    /**
     * Cursor API model id from bot profile when not the placeholder stub/stub; otherwise null.
     */
    public static String fromBotProfile(ModelProfile profile) {
        if (profile == null) {
            return null;
        }
        if ("stub".equalsIgnoreCase(profile.getProvider()) && "stub".equalsIgnoreCase(profile.getModelId())) {
            return null;
        }
        String id = profile.getModelId();
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.trim();
    }

    /**
     * Effective model: {@code cursorModel} or {@code model} from args, else {@code __botDefaultCursorModel}, else {@code CURSOR_MODEL}.
     */
    public static String resolveForLaunch(Map<String, Object> args) {
        String explicit = firstNonBlank(getString(args, "cursorModel"), getString(args, "model"));
        String botDefault = getString(args, "__botDefaultCursorModel");
        return firstNonBlank(explicit, firstNonBlank(botDefault, Env.get("CURSOR_MODEL", "")));
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
