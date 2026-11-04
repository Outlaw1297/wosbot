package dev.frostguard.engine.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.ImageRegionStability;
import dev.frostguard.api.domain.RawImageData;

class NavigationHelperEventTabScrollTest {

    @Test
    void eventTabSwipesStayInsideHeaderStripWithTimedGestures() {
        PointData searchTopLeft = NavigationHelper.EVENT_TAB_STRIP_AREA.topLeft();
        PointData searchBottomRight = NavigationHelper.EVENT_TAB_STRIP_AREA.bottomRight();

        assertTrue(isInside(searchTopLeft, searchBottomRight, NavigationHelper.EVENT_TAB_LEFT_FROM));
        assertTrue(isInside(searchTopLeft, searchBottomRight, NavigationHelper.EVENT_TAB_LEFT_TO));
        assertTrue(isInside(searchTopLeft, searchBottomRight, NavigationHelper.EVENT_TAB_RIGHT_FROM));
        assertTrue(isInside(searchTopLeft, searchBottomRight, NavigationHelper.EVENT_TAB_RIGHT_TO));

        assertTrue(NavigationHelper.EVENT_TAB_LEFT_FROM.getX() < NavigationHelper.EVENT_TAB_LEFT_TO.getX());
        assertTrue(NavigationHelper.EVENT_TAB_RIGHT_FROM.getX() > NavigationHelper.EVENT_TAB_RIGHT_TO.getX());
        assertEquals(NavigationHelper.EVENT_TAB_RIGHT_FROM.getY(), NavigationHelper.EVENT_TAB_RIGHT_TO.getY());

        assertEquals(500, NavigationHelper.EVENT_TAB_SWIPE_DURATION_MS);
        assertEquals(2, NavigationHelper.EVENT_TAB_EDGE_STABLE_CONFIRMATIONS);
        assertEquals(14, NavigationHelper.EVENT_TAB_MAX_SWIPES_PER_DIRECTION);
        assertTrue(NavigationHelper.EVENT_TAB_RIGHT_FROM.getX() - NavigationHelper.EVENT_TAB_RIGHT_TO.getX() >= 300);
    }

    @Test
    void eventTabStripStabilityTreatsIdenticalHeaderAsEdge() {
        byte[] pixels = new byte[720 * 210 * 4];
        RawImageData frame = RawImageData.capture(pixels, 720, 210, 32);
        assertTrue(ImageRegionStability.unchanged(frame, frame, NavigationHelper.EVENT_TAB_STRIP_AREA));
    }

    private static boolean isInside(PointData topLeft, PointData bottomRight, PointData point) {
        return point.getX() >= topLeft.getX()
                && point.getX() <= bottomRight.getX()
                && point.getY() >= topLeft.getY()
                && point.getY() <= bottomRight.getY();
    }
}
