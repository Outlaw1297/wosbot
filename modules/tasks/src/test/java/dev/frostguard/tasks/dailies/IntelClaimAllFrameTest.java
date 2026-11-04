package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.engine.nav.CommonGameAreas;
import dev.frostguard.vision.match.OpenCvPatternLocator;

class IntelClaimAllFrameTest {

    @BeforeAll
    static void loadOpenCv() throws IOException {
        try {
            OpenCvPatternLocator.loadNativeLibrary();
        } catch (UnsatisfiedLinkError ignored) {
            // Another frame test may already have loaded the native library in this JVM.
        }
    }

    @Test
    void detectsClaimAllInTheCompletedIntelMap() throws IOException {
        assertTrue(matches(TemplatesEnum.INTEL_CLAIM_ALL, "intel-claim-all-live.png",
                CommonGameAreas.INTEL_CLAIM_ALL_AREA, 88));
    }

    @Test
    void detectsDualBottomButtonsOnModernIntelMap() throws IOException {
        assertTrue(matches(TemplatesEnum.INTEL_CLAIM_ALL_DUAL, "intel-dual-buttons-20261104.png",
                CommonGameAreas.INTEL_CLAIM_ALL_AREA, 88));
        assertTrue(matches(TemplatesEnum.INTEL_EXECUTE_ALL, "intel-dual-buttons-20261104.png",
                CommonGameAreas.INTEL_EXECUTE_ALL_AREA, 88));
    }

    @Test
    void rejectsClaimAllOnTheJourneyVictoryScreen() throws IOException {
        assertFalse(matches(TemplatesEnum.INTEL_CLAIM_ALL, "journey-victory-20260821.png",
                CommonGameAreas.INTEL_CLAIM_ALL_AREA, 88));
        assertFalse(matches(TemplatesEnum.INTEL_CLAIM_ALL_DUAL, "journey-victory-20260821.png",
                CommonGameAreas.INTEL_CLAIM_ALL_AREA, 88));
    }

    private boolean matches(
            TemplatesEnum template,
            String fixture,
            dev.frostguard.api.domain.AreaData area,
            double threshold) throws IOException {
        return OpenCvPatternLocator.locatePattern(resource(fixture), template,
                area.topLeft(), area.bottomRight(), threshold).isFound();
    }

    private byte[] resource(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/intel/" + name)) {
            return Objects.requireNonNull(stream, "Missing test fixture: " + name).readAllBytes();
        }
    }
}
