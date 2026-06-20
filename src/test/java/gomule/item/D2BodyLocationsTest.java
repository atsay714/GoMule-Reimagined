package gomule.item;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class D2BodyLocationsTest {

    @Test
    public void unknownClassGetsAGenericNonNullGrid() {
        Point[] locs = D2BodyLocations.generateSkillLocs(7); // Warlock: no hand-tuned layout exists
        assertNotNull(locs);
        for (int i = 0; i < locs.length; i++) {
            assertNotNull(locs[i], "skill slot " + i + " should not be null");
        }
    }
}
