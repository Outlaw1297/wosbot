package dev.frostguard.engine.helper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.engine.nav.CommonGameAreas;
import dev.frostguard.vision.match.OpenCvPatternLocator;

class MarchPanelCollapseFrameTest {

    @BeforeAll
    static void loadOpenCv() throws IOException {
        try {
            OpenCvPatternLocator.loadNativeLibrary();
        } catch (UnsatisfiedLinkError ignored) {
            // Another frame test may already have loaded the native library in this JVM.
        }
    }

    @Test
    void detectsExpandedMarchingHeaderAndCollapseCaret() throws IOException {
        byte[] frame = resource("world-march-expanded-redacted-20261104.png");
        assertTrue(OpenCvPatternLocator.locatePattern(
                frame,
                TemplatesEnum.MARCHES_AREA_HEADER,
                CommonGameAreas.MARCH_PANEL_HEADER_AREA.topLeft(),
                CommonGameAreas.MARCH_PANEL_HEADER_AREA.bottomRight(),
                88).isFound());
        assertTrue(OpenCvPatternLocator.locatePattern(
                frame,
                TemplatesEnum.MARCHES_AREA_COLLAPSE_CARET,
                CommonGameAreas.MARCH_PANEL_COLLAPSE_AREA.topLeft(),
                CommonGameAreas.MARCH_PANEL_COLLAPSE_AREA.bottomRight(),
                88).isFound());
    }

    private byte[] resource(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/navigation/" + name)) {
            return Objects.requireNonNull(stream, "Missing test fixture: " + name).readAllBytes();
        }
    }
}
