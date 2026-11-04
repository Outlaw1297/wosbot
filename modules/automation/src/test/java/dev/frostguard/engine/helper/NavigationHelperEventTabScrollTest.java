package dev.frostguard.engine.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.frostguard.api.domain.PointData;

class NavigationHelperEventTabScrollTest {

    @Test
    void eventTabSwipesStayInsideHeaderStripWithTimedGestures() {
        PointData searchTopLeft = new PointData(0, 80);
        PointData searchBottomRight = new PointData(720, 210);

        assertTrue(isInside(searchTopLeft, searchBottomRight, NavigationHelper.EVENT_TAB_RESET_FROM));
        assertTrue(isInside(searchTopLeft, searchBottomRight, NavigationHelper.EVENT_TAB_RESET_TO));
        assertTrue(isInside(searchTopLeft, searchBottomRight, NavigationHelper.EVENT_TAB_SCAN_FROM));
        assertTrue(isInside(searchTopLeft, searchBottomRight, NavigationHelper.EVENT_TAB_SCAN_TO));

        assertTrue(NavigationHelper.EVENT_TAB_RESET_FROM.getX() < NavigationHelper.EVENT_TAB_RESET_TO.getX());
        assertTrue(NavigationHelper.EVENT_TAB_SCAN_FROM.getX() > NavigationHelper.EVENT_TAB_SCAN_TO.getX());
        assertEquals(NavigationHelper.EVENT_TAB_SCAN_FROM.getY(), NavigationHelper.EVENT_TAB_SCAN_TO.getY());

        assertEquals(500, NavigationHelper.EVENT_TAB_SWIPE_DURATION_MS);
        assertEquals(3, NavigationHelper.EVENT_TAB_RESET_SWIPES);
        assertEquals(8, NavigationHelper.EVENT_TAB_SCAN_SWIPES);
        assertTrue(NavigationHelper.EVENT_TAB_SCAN_FROM.getX() - NavigationHelper.EVENT_TAB_SCAN_TO.getX() >= 300);
    }

    private static boolean isInside(PointData topLeft, PointData bottomRight, PointData point) {
        return point.getX() >= topLeft.getX()
                && point.getX() <= bottomRight.getX()
                && point.getY() >= topLeft.getY()
                && point.getY() <= bottomRight.getY();
    }
}
