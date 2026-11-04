package dev.frostguard.tasks.lifecycle;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GettingPopularPromptPatternEvidenceTest {

    private static final PointData TITLE_TOP_LEFT = new PointData(120, 250);
    private static final PointData TITLE_BOTTOM_RIGHT = new PointData(600, 430);
    private static final PointData AMAZING_TOP_LEFT = new PointData(150, 760);
    private static final PointData AMAZING_BOTTOM_RIGHT = new PointData(570, 920);

    @BeforeAll
    static void loadOpenCv() throws IOException {
        try {
            OpenCvPatternLocator.loadNativeLibrary();
        } catch (UnsatisfiedLinkError ignored) {
            // Another saved-frame test may already have loaded OpenCV in this JVM.
        }
    }

    @Test
    void detectsTitleAndAmazingButtonFromSavedFrame() throws IOException {
        Evidence evidence = inspect("/startup/getting-popular-dialog-20261104.png");

        assertTrue(evidence.title().isFound());
        assertTrue(evidence.amazing().isFound());
        assertTrue(evidence.amazing().hasMatchedArea());
        assertTrue(Math.abs(evidence.amazing().getPoint().getX() - 366) <= 40,
                () -> "expected Amazing near center x≈366, got " + evidence.amazing().getPoint());
        assertTrue(Math.abs(evidence.amazing().getPoint().getY() - 835) <= 40,
                () -> "expected Amazing near y≈835, got " + evidence.amazing().getPoint());
    }

    @Test
    void rejectsWelcomeBackMandatoryUpdateAndResourceDownloadDialogs() throws IOException {
        for (String path : new String[] {
                "/startup/welcome-back-dialog-20260821.png",
                "/startup/mandatory-update-dialog-20260820.png",
                "/startup/resource-download-prompt-20260817.png" }) {
            Evidence evidence = inspect(path);
            assertFalse(evidence.title().isFound() && evidence.amazing().isFound(), path);
        }
    }

    private static Evidence inspect(String path) throws IOException {
        byte[] frame = bytes(path);
        ImageSearchResultData title = OpenCvPatternLocator.locatePattern(
                frame, TemplatesEnum.GAME_START_GETTING_POPULAR_TITLE.getTemplate(),
                TITLE_TOP_LEFT, TITLE_BOTTOM_RIGHT, 90);
        ImageSearchResultData amazing = OpenCvPatternLocator.locatePattern(
                frame, TemplatesEnum.GAME_START_GETTING_POPULAR_AMAZING_BUTTON.getTemplate(),
                AMAZING_TOP_LEFT, AMAZING_BOTTOM_RIGHT, 90);
        return new Evidence(title, amazing);
    }

    private static byte[] bytes(String path) throws IOException {
        try (InputStream stream = GettingPopularPromptPatternEvidenceTest.class.getResourceAsStream(path)) {
            return Objects.requireNonNull(stream, "Missing test resource: " + path).readAllBytes();
        }
    }

    private record Evidence(ImageSearchResultData title, ImageSearchResultData amazing) {
    }
}
