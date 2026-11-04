package dev.frostguard.engine.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.engine.emulator.EmulatorController;
import dev.frostguard.engine.input.TapInteractionService;
import dev.frostguard.engine.nav.CommonGameAreas;
import dev.frostguard.engine.service.BotOcrEngine;
import dev.frostguard.vision.logging.ProfileContextLogger;
import dev.frostguard.vision.ocr.ResilientOcrExecutor;

// Handles character verification and switching via the in-game profile menu.
public class CharacterSwitchHelper {

    public static final int CHARACTER_SWITCH_RELOAD_DELAY_MS = 5000;

    // Nested roster lists ignore instant default swipes; match sidebar timing.
    static final int ROSTER_SCAN_PASSES = 6;
    static final int ROSTER_SCROLL_DURATION_MS = 500;
    static final int ROSTER_SCROLL_SETTLE_MS = 1000;
    // Chief Profile animates in after the avatar tap; OCR before the settings
    // control is visible reads loading garbage (power, "ID …", fragments).
    static final int PROFILE_OPEN_SETTLE_MS = 2000;
    static final int PROFILE_READY_ATTEMPTS = 10;
    static final int PROFILE_READY_RETRY_DELAY_MS = 500;
    static final int PROFILE_OCR_SETTLE_MS = 1000;
    static final int PROFILE_OCR_READ_ATTEMPTS = 3;
    static final int PROFILE_MENU_BACK_SETTLE_MS = 800;
    static final int ROSTER_OPEN_SETTLE_MS = 2500;
    // In-game character IDs are long numeric strings; short digit runs are furnace/power bleed.
    static final int MIN_CHARACTER_ID_DIGITS = 6;
    static final String NAME_OCR_ALLOWED_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-";

    private final EmulatorController emu;
    private final String dev;
    private final TapInteractionService taps;
    private final ProfileContextLogger log;
    private final TemplateSearchHelper tpl;
    private final ResilientOcrExecutor<String> ocr;
    private boolean lastSwitchRequiredReload;
    private boolean lastVerifyUnreadable;
    private ImageSearchResultData readySettingsHit;

    public CharacterSwitchHelper(EmulatorController emuManager, String emulatorNumber, AccountDescriptor profile) {
        this.emu = emuManager;
        this.dev = emulatorNumber;
        this.taps = TapInteractionService.forController(emuManager, emulatorNumber);
        this.log = new ProfileContextLogger(CharacterSwitchHelper.class, profile);
        this.tpl = new TemplateSearchHelper(emuManager, emulatorNumber, profile);
        this.ocr = new ResilientOcrExecutor<>(new BotOcrEngine(emuManager, emulatorNumber));
    }

    /** True when the last successful {@link #switchToCharacter} confirmed a real switch. */
    public boolean lastSwitchRequiredReload() {
        return lastSwitchRequiredReload;
    }

    /**
     * True when the last {@link #verifyCurrentCharacter} failed because Chief Profile OCR
     * produced no usable name or ID. Callers must not treat that as a character mismatch.
     */
    public boolean lastVerifyUnreadable() {
        return lastVerifyUnreadable;
    }

    // --- Character verification ---

    public boolean verifyCurrentCharacter(AccountDescriptor p) {
        lastVerifyUnreadable = false;
        String wantName = p.getCharacterName(), wantId = p.getCharacterId();
        if (blank(wantName) && blank(wantId)) { log.debug("No character config, skipping"); return true; }
        log.info("Verifying character: name='" + wantName + "' id='" + wantId + "'");

        taps.tapInside(CommonGameAreas.PROFILE_AVATAR, 1, PROFILE_OPEN_SETTLE_MS);
        if (!waitForChiefProfileReady()) {
            log.warn("Chief Profile did not finish loading before OCR");
            lastVerifyUnreadable = true;
            return false;
        }
        sleep(PROFILE_OCR_SETTLE_MS);

        LiveCharacter live = readLiveCharacterWithRetries(wantName, wantId);
        log.info("Live char: name='" + live.name() + "' id='" + live.id() + "'");

        if (!hasUsableIdentity(live, wantName, wantId)) {
            lastVerifyUnreadable = true;
            log.warn("Chief Profile OCR unreadable; not treating as character mismatch");
            emu.pressBack(dev);
            return false;
        }

        if (matchesConfigured(live, wantName, wantId)) {
            log.info("Char verified OK from Chief Profile");
            emu.pressBack(dev);
            return true;
        }
        log.warn("Char mismatch; leaving Chief Profile open for switch");
        return false;
    }

    // --- Character switching ---

    public boolean switchToCharacter(AccountDescriptor p) {
        lastSwitchRequiredReload = false;
        String target = p.getCharacterName();
        if (blank(target)) { log.error("No character name configured"); return false; }
        log.info("Switching to '" + target + "' server=" + p.getCharacterServer());

        if (!ensureChiefProfileReadyForSwitch()) {
            log.error("Chief Profile settings control not ready for switch");
            return false;
        }
        if (!tapChiefProfileSettings()) {
            log.error("Settings button not found"); return false;
        }
        sleep(PROFILE_MENU_BACK_SETTLE_MS);
        if (!tapTemplate(TemplatesEnum.GAME_PROFILE_SETTINGS_SWITCH_CHARACTER_BUTTON, null)) {
            log.error("Switch Character button not found"); emu.pressBack(dev); return false;
        }
        sleep(ROSTER_OPEN_SETTLE_MS);

        for (int pass = 0; pass < ROSTER_SCAN_PASSES; pass++) {
            log.info("Roster scan " + (pass + 1) + "/" + ROSTER_SCAN_PASSES);
            ImageSearchResultData hit = findInRoster(target);
            if (hit == null) {
                if (pass + 1 >= ROSTER_SCAN_PASSES) {
                    break;
                }
                log.info("Target not visible, scrolling roster durationMs=" + ROSTER_SCROLL_DURATION_MS);
                scrollDown();
                continue;
            }

            // Roster checkmark alone is not proof — confirm from Chief Profile OCR.
            if (checkmarkAt(hit.getPoint())) {
                log.info("Roster shows already active — confirming from Chief Profile");
                return confirmAlreadyActiveFromChiefProfile(p);
            }

            // Tap and confirm
            taps.tapInside(hit, 1, 500); sleep(500);
            if (confirmSwitch(target)) {
                lastSwitchRequiredReload = true;
                sleep(CHARACTER_SWITCH_RELOAD_DELAY_MS);
                log.info("Switch OK");
                return true;
            }
            log.warn("Confirm failed, canceling");
            cancelSwitch();
        }
        log.error("Character not found after " + ROSTER_SCAN_PASSES + " passes — closing emulator");
        emu.closeEmulator(dev); return false;
    }

    // --- Roster scanning (merged active + inactive search) ---

    private ImageSearchResultData findInRoster(String targetName) {
        TemplateSearchHelper.SearchConfig cfg = TemplateSearchHelper.SearchConfig.builder()
                .withArea(CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_CHARACTER_LIST_AREA)
                .withMaxAttempts(3).withThreshold(90).withMaxResults(5).build();

        List<ImageSearchResultData> hits = new ArrayList<>();
        addAll(hits, tpl.locateAllPatterns(TemplatesEnum.GAME_PROFILE_SETTINGS_CHARACTER_FURNACE_LEVEL_ACTIVE, cfg));
        addAll(hits, tpl.locateAllPatterns(TemplatesEnum.GAME_PROFILE_SETTINGS_CHARACTER_FURNACE_LEVEL_INACTIVE, cfg));
        if (hits.isEmpty()) {
            log.info("No furnace indicators in current roster viewport");
            return null;
        }
        log.debug(hits.size() + " furnace indicators found");

        for (ImageSearchResultData h : hits) {
            PointData fp = h.getPoint();
            String name = ocr.attemptRecognition(
                    new PointData(CommonGameAreas.CHARACTER_NAME_ABOVE_FURNACE_X_START,
                            fp.getY() - CommonGameAreas.CHARACTER_NAME_ABOVE_FURNACE_TOP_OFFSET_Y),
                    new PointData(CommonGameAreas.CHARACTER_NAME_ABOVE_FURNACE_X_END,
                            fp.getY() - CommonGameAreas.CHARACTER_NAME_ABOVE_FURNACE_BOTTOM_OFFSET_Y),
                    2, 200, nameOcrCfg(), t -> t != null && !t.trim().isEmpty(), String::trim);
            if (name != null && namesMatch(name, targetName)) { log.info("Found: '" + name + "'"); return h; }
        }
        return null;
    }

    private boolean checkmarkAt(PointData fp) {
        int cx = fp.getX() + 264, cy = fp.getY() - 40;
        return tpl.locatePattern(TemplatesEnum.GAME_PROFILE_SETTINGS_CHARACTER_ACTIVE_CHECKMARK,
                TemplateSearchHelper.SearchConfig.builder()
                        .withArea(new AreaData(new PointData(cx, cy), new PointData(cx + 60, cy + 60)))
                        .withMaxAttempts(1).withThreshold(90).build()).isFound();
    }

    private boolean confirmAlreadyActiveFromChiefProfile(AccountDescriptor p) {
        // Switch Character list -> Settings -> Chief Profile
        emu.pressBack(dev);
        sleep(PROFILE_MENU_BACK_SETTLE_MS);
        emu.pressBack(dev);
        sleep(PROFILE_OPEN_SETTLE_MS);
        if (!waitForChiefProfileReady()) {
            log.warn("Could not return to Chief Profile after roster already-active");
            return false;
        }
        sleep(PROFILE_OCR_SETTLE_MS);
        LiveCharacter live = readLiveCharacter();
        log.debug("Live char after roster already-active: name='" + live.name() + "' id='" + live.id() + "'");
        if (!matchesConfigured(live, p.getCharacterName(), p.getCharacterId())) {
            log.warn("Roster checkmark present but Chief Profile OCR mismatch");
            return false;
        }
        log.info("Char verified OK from Chief Profile after roster already-active");
        emu.pressBack(dev);
        sleep(PROFILE_MENU_BACK_SETTLE_MS);
        lastSwitchRequiredReload = false;
        return true;
    }

    // --- Confirm / cancel dialog ---

    private boolean confirmSwitch(String expectedName) {
        String dialogName = ocr.attemptRecognition(
                CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_CONFIRM_DIALOG_NAME_OCR_AREA.topLeft(),
                CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_CONFIRM_DIALOG_NAME_OCR_AREA.bottomRight(),
                2, 200, nameOcrCfg(), t -> t != null && !t.trim().isEmpty(), String::trim);
        if (blank(dialogName) || !namesMatch(dialogName, expectedName)) {
            log.warn("Dialog name mismatch: got='" + dialogName + "' want='" + expectedName + "'");
            return false;
        }
        log.info("Dialog name OK: '" + dialogName + "'");
        return tapTemplate(TemplatesEnum.GAME_PROFILE_SETTINGS_SWITCH_CHARACTER_CONFIRM_BUTTON,
                CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_PROMPT_BUTTON_AREA);
    }

    private void cancelSwitch() {
        ImageSearchResultData btn = tpl.locatePattern(
                TemplatesEnum.GAME_PROFILE_SETTINGS_SWITCH_CHARACTER_CANCEL_BUTTON,
                TemplateSearchHelper.SearchConfig.builder()
                        .withArea(CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_PROMPT_BUTTON_AREA)
                        .withMaxAttempts(3).withDelay(500).withThreshold(80).build());
        if (btn == null || !btn.isFound()) emu.pressBack(dev);
        else taps.tapInside(btn, 1, 500);
        sleep(500);
    }

    // --- Shared utilities ---

    private boolean ensureChiefProfileReadyForSwitch() {
        if (waitForChiefProfileReady()) {
            return true;
        }
        // Verify may have closed the panel, or the avatar tap never opened it.
        log.info("Re-opening Chief Profile for character switch");
        taps.tapInside(CommonGameAreas.PROFILE_AVATAR, 1, PROFILE_OPEN_SETTLE_MS);
        return waitForChiefProfileReady();
    }

    private boolean waitForChiefProfileReady() {
        readySettingsHit = tpl.locatePattern(
                TemplatesEnum.GAME_PROFILE_SETTINGS_BUTTON,
                TemplateSearchHelper.SearchConfig.builder()
                        .withArea(CommonGameAreas.PROFILE_SETTINGS_BUTTON_AREA)
                        .withMaxAttempts(PROFILE_READY_ATTEMPTS)
                        .withDelay(PROFILE_READY_RETRY_DELAY_MS)
                        .withThreshold(80)
                        .build());
        boolean ready = readySettingsHit != null && readySettingsHit.isFound();
        if (ready) {
            log.debug("Chief Profile ready (settings control visible)");
        } else {
            readySettingsHit = null;
        }
        return ready;
    }

    /**
     * Tap the settings control already located by {@link #waitForChiefProfileReady}.
     * A second search right after a successful wait was racing a transient UI frame
     * and logging "Settings button not found" even though readiness had succeeded.
     */
    private boolean tapChiefProfileSettings() {
        if (readySettingsHit != null && readySettingsHit.isFound()) {
            taps.tapInside(readySettingsHit, 1, 500);
            readySettingsHit = null;
            return true;
        }
        return tapTemplate(TemplatesEnum.GAME_PROFILE_SETTINGS_BUTTON,
                CommonGameAreas.PROFILE_SETTINGS_BUTTON_AREA);
    }

    private LiveCharacter readLiveCharacterWithRetries(String wantName, String wantId) {
        LiveCharacter live = readLiveCharacter();
        for (int attempt = 1; attempt < PROFILE_OCR_READ_ATTEMPTS
                && !hasUsableIdentity(live, wantName, wantId); attempt++) {
            log.debug("Chief Profile OCR retry " + (attempt + 1) + "/" + PROFILE_OCR_READ_ATTEMPTS);
            sleep(PROFILE_OCR_SETTLE_MS);
            live = readLiveCharacter();
        }
        return live;
    }

    private LiveCharacter readLiveCharacter() {
        String liveId = sanitizeCharacterId(digitsOnly(ocrRead(CommonGameAreas.CHARACTER_ID_OCR_AREA, idOcrCfg())));
        String liveName = ocrRead(CommonGameAreas.CHARACTER_NAME_OCR_AREA, nameOcrCfg());
        // Loading frames or a too-low name box can OCR the ID label into the name strip.
        if (looksLikeIdLeak(liveName)) {
            log.debug("Discarding leaked ID text from name OCR: '" + liveName + "'");
            liveName = null;
        }
        return new LiveCharacter(liveName, liveId);
    }

    static String digitsOnly(String ocrText) {
        if (blank(ocrText)) {
            return null;
        }
        String digits = ocrText.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    static String sanitizeCharacterId(String digits) {
        if (blank(digits)) {
            return null;
        }
        if (digits.length() < MIN_CHARACTER_ID_DIGITS) {
            return null;
        }
        return digits;
    }

    static boolean hasUsableIdentity(LiveCharacter live, String wantName, String wantId) {
        boolean needId = !blank(wantId);
        boolean needName = !blank(wantName);
        if (needId && !blank(live.id())) {
            return true;
        }
        if (needName && !blank(live.name())) {
            return true;
        }
        return false;
    }

    private boolean matchesConfigured(LiveCharacter live, String wantName, String wantId) {
        boolean needId = !blank(wantId);
        boolean needName = !blank(wantName);
        boolean idMatched = needId && !blank(live.id()) && wantId.equals(live.id());
        boolean nameMatched = needName && !blank(live.name()) && namesMatch(live.name(), wantName);
        if (needId && blank(live.id())) {
            log.warn("ID configured but OCR failed");
        }
        if (needName && blank(live.name())) {
            log.warn("Name configured but OCR failed");
        }
        if (!needId && !needName) {
            return true;
        }
        return idMatched || nameMatched;
    }

    static boolean looksLikeIdLeak(String ocrText) {
        if (blank(ocrText)) return false;
        String trimmed = ocrText.trim();
        if (trimmed.regionMatches(true, 0, "ID", 0, 2)) return true;
        return trimmed.chars().allMatch(Character::isDigit);
    }

    private boolean tapTemplate(TemplatesEnum t, AreaData area) {
        TemplateSearchHelper.SearchConfig.Builder b = TemplateSearchHelper.SearchConfig.builder()
                .withMaxAttempts(3).withDelay(500).withThreshold(80);
        if (area != null) b.withArea(area);
        ImageSearchResultData hit = tpl.locatePattern(t, b.build());
        if (hit == null || !hit.isFound()) return false;
        taps.tapInside(hit, 1, 500); return true;
    }

    // OCR often drops underscores and spacing; compare alphanumerics only.
    static boolean namesMatch(String ocrText, String expected) {
        if (blank(ocrText) || blank(expected)) return false;
        String a = normalizeName(ocrText);
        String b = normalizeName(expected);
        if (b.isEmpty()) return false;
        return a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT));
    }

    static String normalizeName(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "");
    }

    private String ocrRead(AreaData area, OcrSettingsData cfg) {
        return ocr.attemptRecognition(area, 4, 400L, cfg, t -> t != null && !t.trim().isEmpty(), String::trim);
    }

    private static boolean blank(String s) { return s == null || s.isEmpty(); }

    private void scrollDown() {
        emu.swipeScreen(dev,
                CommonGameAreas.CHARACTER_ROSTER_SCROLL_FROM,
                CommonGameAreas.CHARACTER_ROSTER_SCROLL_TO,
                ROSTER_SCROLL_DURATION_MS);
        sleep(ROSTER_SCROLL_SETTLE_MS);
    }

    private static void addAll(List<ImageSearchResultData> dst, List<ImageSearchResultData> src) {
        if (src != null) dst.addAll(src);
    }

    private OcrSettingsData idOcrCfg() {
        return OcrSettingsData.builder()
                .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                .setAllowedChars("0123456789")
                .setRemoveBackground(true).build();
    }

    static OcrSettingsData nameOcrCfg() {
        return OcrSettingsData.builder()
                .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                .setAllowedChars(NAME_OCR_ALLOWED_CHARS)
                .setRemoveBackground(true).build();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    record LiveCharacter(String name, String id) {}
}
