package edu.kit.kastel.vads.compiler;

import org.junit.jupiter.api.*;

class TestPosition {
    @Test
    void testPositionString() {
        Position pos = new Position.SimplePosition(1, 42);
        Assertions.assertEquals(pos.toString(), "1:42");
    }
}
