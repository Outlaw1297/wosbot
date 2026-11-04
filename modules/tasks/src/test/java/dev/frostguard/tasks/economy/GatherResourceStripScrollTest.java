package dev.frostguard.tasks.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.frostguard.api.domain.PointData;

class GatherResourceStripScrollTest {

    @Test
    void resourceStripSwipesStayInsideStripWithTimedGestures() {
        PointData topLeft = GatherRoutine.RES_TAB_STRIP_AREA.topLeft();
        PointData bottomRight = GatherRoutine.RES_TAB_STRIP_AREA.bottomRight();

        assertTrue(isInside(topLeft, bottomRight, GatherRoutine.RES_TAB_LEFT_FROM));
        assertTrue(isInside(topLeft, bottomRight, GatherRoutine.RES_TAB_LEFT_TO));
        assertTrue(isInside(topLeft, bottomRight, GatherRoutine.RES_TAB_RIGHT_FROM));
        assertTrue(isInside(topLeft, bottomRight, GatherRoutine.RES_TAB_RIGHT_TO));

        assertTrue(GatherRoutine.RES_TAB_LEFT_FROM.getX() < GatherRoutine.RES_TAB_LEFT_TO.getX());
        assertTrue(GatherRoutine.RES_TAB_RIGHT_FROM.getX() > GatherRoutine.RES_TAB_RIGHT_TO.getX());
        assertEquals(GatherRoutine.RES_TAB_RIGHT_FROM.getY(), GatherRoutine.RES_TAB_RIGHT_TO.getY());
        assertEquals(500, GatherRoutine.RES_TAB_SWIPE_DURATION_MS);
        assertEquals(2, GatherRoutine.RES_TAB_EDGE_STABLE_CONFIRMATIONS);
    }

    private static boolean isInside(PointData topLeft, PointData bottomRight, PointData point) {
        return point.getX() >= topLeft.getX()
                && point.getX() <= bottomRight.getX()
                && point.getY() >= topLeft.getY()
                && point.getY() <= bottomRight.getY();
    }
}
