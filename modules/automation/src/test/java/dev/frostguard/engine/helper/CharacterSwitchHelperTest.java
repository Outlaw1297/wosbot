package dev.frostguard.engine.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.engine.nav.CommonGameAreas;

class CharacterSwitchHelperTest {

    @Test
    void rosterScrollUsesTimedSwipeInsideListArea() {
        PointData from = CommonGameAreas.CHARACTER_ROSTER_SCROLL_FROM;
        PointData to = CommonGameAreas.CHARACTER_ROSTER_SCROLL_TO;
        var list = CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_CHARACTER_LIST_AREA;

        assertTrue(isInside(list, from));
        assertTrue(isInside(list, to));
        assertEquals(from.getX(), to.getX());
        assertTrue(from.getY() > to.getY());
        assertEquals(500, CharacterSwitchHelper.ROSTER_SCROLL_DURATION_MS);
        assertEquals(6, CharacterSwitchHelper.ROSTER_SCAN_PASSES);
    }

    @Test
    void chiefProfileWaitsForSettingsControlBeforeOcr() {
        assertEquals(2000, CharacterSwitchHelper.PROFILE_OPEN_SETTLE_MS);
        assertEquals(10, CharacterSwitchHelper.PROFILE_READY_ATTEMPTS);
        assertEquals(500, CharacterSwitchHelper.PROFILE_READY_RETRY_DELAY_MS);
        assertEquals(1000, CharacterSwitchHelper.PROFILE_OCR_SETTLE_MS);
        assertEquals(2500, CharacterSwitchHelper.ROSTER_OPEN_SETTLE_MS);
        assertTrue(CharacterSwitchHelper.PROFILE_OPEN_SETTLE_MS
                + CharacterSwitchHelper.PROFILE_READY_ATTEMPTS
                        * CharacterSwitchHelper.PROFILE_READY_RETRY_DELAY_MS
                >= 5000);
    }

    private static boolean isInside(dev.frostguard.api.domain.AreaData area, PointData point) {
        return point.getX() >= area.topLeft().getX()
                && point.getX() <= area.bottomRight().getX()
                && point.getY() >= area.topLeft().getY()
                && point.getY() <= area.bottomRight().getY();
    }

    @Test
    void namesMatchWhenOcrDropsUnderscoresOrSpacing() {
        assertTrue(CharacterSwitchHelper.namesMatch("Outlaw1297", "Outlaw_1297"));
        assertTrue(CharacterSwitchHelper.namesMatch("Outlaw_1297", "Outlaw_1297"));
        assertTrue(CharacterSwitchHelper.namesMatch("Outlaw 1297", "Outlaw_1297"));
        assertTrue(CharacterSwitchHelper.namesMatch("OUTLAW_1297", "Outlaw_1297"));
        assertTrue(CharacterSwitchHelper.namesMatch("REXOutlaw_1297", "Outlaw_1297"));
        assertFalse(CharacterSwitchHelper.namesMatch("OtherPlayer", "Outlaw_1297"));
        assertFalse(CharacterSwitchHelper.namesMatch("", "Outlaw_1297"));
        assertEquals("Outlaw1297", CharacterSwitchHelper.normalizeName("Outlaw_1297"));
    }

    @Test
    void discardsIdLabelLeaksFromNameOcr() {
        assertTrue(CharacterSwitchHelper.looksLikeIdLeak("ID 181743604"));
        assertTrue(CharacterSwitchHelper.looksLikeIdLeak("id181743604"));
        assertTrue(CharacterSwitchHelper.looksLikeIdLeak("8641"));
        assertFalse(CharacterSwitchHelper.looksLikeIdLeak("Outlaw_1297"));
        assertFalse(CharacterSwitchHelper.looksLikeIdLeak("[REX]Outlaw_1297"));
        assertFalse(CharacterSwitchHelper.looksLikeIdLeak(""));
    }

    @Test
    void nameOcrAllowsUnderscoreAndHyphen() {
        String allowed = CharacterSwitchHelper.nameOcrCfg().getAllowedChars();
        assertTrue(allowed.contains("_"));
        assertTrue(allowed.contains("-"));
        assertTrue(allowed.contains(" "));
        assertEquals(CharacterSwitchHelper.NAME_OCR_ALLOWED_CHARS, allowed);
    }
}
