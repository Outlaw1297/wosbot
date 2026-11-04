package dev.frostguard.engine.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;

class HorizontalStripSeekerTest {

    @Test
    void returnsImmediatelyWhenAlreadyVisible() {
        RecordingHost host = new RecordingHost();
        ImageSearchResultData hit = ImageSearchResultData.hit(10, 10, 95, 40, 20);
        ImageSearchResultData found = HorizontalStripSeeker.seek(
                host,
                sampleConfig(),
                "strip",
                () -> hit);
        assertTrue(found.isFound());
        assertEquals(0, host.swipes.size());
    }

    @Test
    void rewindsLeftThenWalksRightUntilFound() {
        // Identical frames end the left seek after two stable edge confirmations.
        RecordingHost host = new RecordingHost(true);
        AtomicInteger attempts = new AtomicInteger();
        ImageSearchResultData found = HorizontalStripSeeker.seek(
                host,
                sampleConfig(),
                "strip",
                () -> {
                    int n = attempts.incrementAndGet();
                    // miss: initial + 2 left settles; hit on first right settle
                    if (n >= 4) {
                        return ImageSearchResultData.hit(12, 12, 96, 40, 20);
                    }
                    return ImageSearchResultData.miss();
                });
        assertTrue(found.isFound());
        assertEquals(3, host.swipes.size());
        assertEquals("left", host.swipes.get(0));
        assertEquals("left", host.swipes.get(1));
        assertEquals("right", host.swipes.get(2));
    }

    @Test
    void stopsAtStableLeftEdgeWithoutFindingTarget() {
        RecordingHost host = new RecordingHost(true);
        ImageSearchResultData found = HorizontalStripSeeker.seek(
                host,
                sampleConfig(),
                "strip",
                ImageSearchResultData::miss);
        assertTrue(!found.isFound());
        // two left swipes that leave the strip unchanged end the left seek
        assertEquals(2, host.swipes.stream().filter("left"::equals).count());
    }

    private static HorizontalStripSeeker.Config sampleConfig() {
        return new HorizontalStripSeeker.Config(
                AreaData.of(0, 80, 720, 210),
                new PointData(80, 120),
                new PointData(578, 130),
                new PointData(630, 143),
                new PointData(280, 143),
                500,
                0L,
                2,
                4);
    }

    private static final class RecordingHost implements HorizontalStripSeeker.Host {
        private final List<String> swipes = new ArrayList<>();
        private final boolean identicalFrames;
        private final byte[] pixels = new byte[720 * 210 * 4];

        private RecordingHost() {
            this(false);
        }

        private RecordingHost(boolean identicalFrames) {
            this.identicalFrames = identicalFrames;
        }

        @Override
        public RawImageData captureStrip() {
            if (!identicalFrames) {
                // mutate one byte so consecutive captures differ until tests force stability
                pixels[swipes.size() % pixels.length] = (byte) (swipes.size() + 1);
            }
            return RawImageData.capture(pixels.clone(), 720, 210, 32);
        }

        @Override
        public void swipe(PointData from, PointData to, int durationMs) {
            swipes.add(from.getX() < to.getX() ? "left" : "right");
        }

        @Override
        public void settle(long settleMs) {
        }

        @Override
        public void debug(String message) {
        }
    }
}
