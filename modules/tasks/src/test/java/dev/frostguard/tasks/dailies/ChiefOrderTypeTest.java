package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import dev.frostguard.api.configs.TemplatesEnum;

class ChiefOrderTypeTest {

    @Test
    void currentShelfOrdersExposeDistinctTemplatesAndCooldowns() {
        Set<TemplatesEnum> templates = Arrays.stream(ChiefOrderRoutine.ChiefOrderType.values())
                .map(ChiefOrderRoutine.ChiefOrderType::getTemplate)
                .collect(Collectors.toSet());
        assertEquals(ChiefOrderRoutine.ChiefOrderType.values().length, templates.size());

        assertEquals(24, ChiefOrderRoutine.ChiefOrderType.RUSH_JOB.getCooldownHours());
        assertEquals(12, ChiefOrderRoutine.ChiefOrderType.CRUNCH_TIME.getCooldownHours());
        assertEquals(12, ChiefOrderRoutine.ChiefOrderType.COMPREHENSIVE_CARE.getCooldownHours());
        assertEquals(12, ChiefOrderRoutine.ChiefOrderType.DOUBLE_TIME.getCooldownHours());
        assertEquals(12, ChiefOrderRoutine.ChiefOrderType.ADVANCED_TRAINING.getCooldownHours());
        assertEquals(12, ChiefOrderRoutine.ChiefOrderType.DAWN_ALLIANCE_COMMENDATION.getCooldownHours());

        assertTrue(templates.contains(TemplatesEnum.CHIEF_ORDER_CRUNCH_TIME));
        assertTrue(templates.contains(TemplatesEnum.CHIEF_ORDER_COMPREHENSIVE_CARE));
        assertTrue(templates.contains(TemplatesEnum.CHIEF_ORDER_DOUBLE_TIME));
        assertTrue(templates.contains(TemplatesEnum.CHIEF_ORDER_ADVANCED_TRAINING));
        assertTrue(templates.contains(TemplatesEnum.CHIEF_ORDER_DAWN_ALLIANCE_COMMENDATION));
        assertFalse(templates.contains(TemplatesEnum.CHIEF_ORDER_ENACT_BUTTON));
    }
}
