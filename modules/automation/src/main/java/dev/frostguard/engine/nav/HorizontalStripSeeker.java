package dev.frostguard.engine.nav;

import java.util.Objects;
import java.util.function.Supplier;

import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.ImageRegionStability;

/**
 * Rewinds a horizontal icon/tab strip to its left edge, then walks right by a
 * fixed timed pull until the target appears or the strip stops changing.
 * Instant default swipes are ignored by several Whiteout Survival carousels.
 */
public final class HorizontalStripSeeker {

    public record Config(
            AreaData stripArea,
            PointData leftFrom,
            PointData leftTo,
            PointData rightFrom,
            PointData rightTo,
            int durationMs,
            long settleMs,
            int edgeStableConfirmations,
            int maxSwipesPerDirection) {
        public Config {
            Objects.requireNonNull(stripArea, "stripArea");
            Objects.requireNonNull(leftFrom, "leftFrom");
            Objects.requireNonNull(leftTo, "leftTo");
            Objects.requireNonNull(rightFrom, "rightFrom");
            Objects.requireNonNull(rightTo, "rightTo");
            if (durationMs <= 0) {
                throw new IllegalArgumentException("durationMs must be positive");
            }
            if (settleMs < 0) {
                throw new IllegalArgumentException("settleMs must be non-negative");
            }
            if (edgeStableConfirmations <= 0) {
                throw new IllegalArgumentException("edgeStableConfirmations must be positive");
            }
            if (maxSwipesPerDirection <= 0) {
                throw new IllegalArgumentException("maxSwipesPerDirection must be positive");
            }
        }
    }

    public interface Host {
        RawImageData captureStrip();

        void swipe(PointData from, PointData to, int durationMs);

        void settle(long settleMs);

        void debug(String message);
    }

    private HorizontalStripSeeker() { }

    public static ImageSearchResultData seek(
            Host host,
            Config config,
            String label,
            Supplier<ImageSearchResultData> locate) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(locate, "locate");

        ImageSearchResultData found = locate.get();
        if (found.isFound()) {
            return found;
        }

        found = swipeUntilEdgeOrFound(
                host, config, label, "left", config.leftFrom(), config.leftTo(), locate);
        if (found.isFound()) {
            return found;
        }
        return swipeUntilEdgeOrFound(
                host, config, label, "right", config.rightFrom(), config.rightTo(), locate);
    }

    private static ImageSearchResultData swipeUntilEdgeOrFound(
            Host host,
            Config config,
            String label,
            String direction,
            PointData from,
            PointData to,
            Supplier<ImageSearchResultData> locate) {
        int stableConfirmations = 0;
        ImageSearchResultData found = ImageSearchResultData.miss();
        for (int swipe = 1; swipe <= config.maxSwipesPerDirection(); swipe++) {
            RawImageData before = host.captureStrip();
            host.debug(label + " " + direction + " swipe " + swipe + "/"
                    + config.maxSwipesPerDirection()
                    + " durationMs=" + config.durationMs());
            host.swipe(from, to, config.durationMs());
            host.settle(config.settleMs());

            found = locate.get();
            if (found.isFound()) {
                host.debug(label + " found during " + direction + " seek after swipe " + swipe);
                return found;
            }

            RawImageData after = host.captureStrip();
            boolean unchanged = before != null && after != null
                    && ImageRegionStability.unchanged(before, after, config.stripArea());
            if (unchanged) {
                stableConfirmations++;
                host.debug(label + " strip unchanged after " + direction + " swipe ("
                        + stableConfirmations + "/" + config.edgeStableConfirmations() + ")");
                if (stableConfirmations >= config.edgeStableConfirmations()) {
                    host.debug(label + " strip reached " + direction + " edge");
                    return found;
                }
            } else {
                stableConfirmations = 0;
            }
        }
        host.debug(label + " " + direction + " seek hit swipe budget without a stable edge");
        return found;
    }
}
