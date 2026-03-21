package com.vinekeepers.workflow.planning;

import java.util.Locale;

/**
 * Detects short human replies that mean "proceed with your best draft" rather than literal field content.
 */
public final class LightweightPlanningReply {

    private LightweightPlanningReply() {}

    public static boolean isConsentToProceed(String answer) {
        if (answer == null) {
            return false;
        }
        String t = answer.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) {
            return false;
        }
        return t.equals("ok")
                || t.equals("okay")
                || t.equals("k")
                || t.equals("yes")
                || t.equals("y")
                || t.equals("accept")
                || t.equals("continue")
                || t.equals("go")
                || t.equals("proceed")
                || t.equals("fine")
                || t.equals("sure")
                || t.equals("sounds good")
                || t.equals("lgtm")
                || t.equals("ship it");
    }
}
