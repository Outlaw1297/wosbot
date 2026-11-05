package dev.frostguard.tasks.lifecycle;

import java.time.Duration;

final class StartupRecoveryPolicy {

    static final int MAX_UNKNOWN_BLOCKER_BACK_ATTEMPTS = 1;
    static final Duration UNKNOWN_BLOCKER_COOLDOWN = Duration.ofMinutes(15);
    static final Duration PLAY_STORE_REDIRECT_COOLDOWN = Duration.ofHours(1);
    static final Duration UPDATE_FOLLOW_UP_COOLDOWN = Duration.ofMinutes(15);
    /** Chief Profile blocked by an overlay or unreadable OCR; yield the slot briefly. */
    static final Duration CHARACTER_VERIFY_COOLDOWN = Duration.ofMinutes(5);
    static final int CHARACTER_VERIFY_ATTEMPTS = 3;

    private StartupRecoveryPolicy() {
    }

    static UnknownBlockerAction forUnknownBlocker(boolean gameForeground, int completedBackAttempts) {
        return gameForeground && completedBackAttempts < MAX_UNKNOWN_BLOCKER_BACK_ATTEMPTS
                ? UnknownBlockerAction.TRY_GAME_BACK
                : UnknownBlockerAction.COOLDOWN_AND_RELEASE_SLOT;
    }

    enum UnknownBlockerAction {
        TRY_GAME_BACK,
        COOLDOWN_AND_RELEASE_SLOT
    }
}
