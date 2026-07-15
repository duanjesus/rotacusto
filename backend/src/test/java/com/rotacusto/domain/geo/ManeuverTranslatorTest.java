package com.rotacusto.domain.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ManeuverTranslatorTest {

    @Test
    void translatesLeftAndRightTurnsWithStreetName() {
        assertEquals("Vire à esquerda em Rua das Flores", ManeuverTranslator.translate(0, "Rua das Flores"));
        assertEquals("Vire à direita em Rua das Flores", ManeuverTranslator.translate(1, "Rua das Flores"));
    }

    @Test
    void translatesStraightAndArrival() {
        assertEquals("Siga em frente em Avenida Brasil", ManeuverTranslator.translate(6, "Avenida Brasil"));
        assertEquals("Chegue ao destino em Avenida Brasil", ManeuverTranslator.translate(10, "Avenida Brasil"));
    }

    @Test
    void omitsStreetNameWhenUnnamedOrDash() {
        assertEquals("Mantenha-se à direita", ManeuverTranslator.translate(13, "-"));
        assertEquals("Mantenha-se à direita", ManeuverTranslator.translate(13, ""));
        assertEquals("Mantenha-se à direita", ManeuverTranslator.translate(13, null));
    }

    @Test
    void fallsBackToGenericContinueForUnknownType() {
        assertEquals("Continue em Rua X", ManeuverTranslator.translate(999, "Rua X"));
    }
}
