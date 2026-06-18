package randall.d2files;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class D2TxtFileTest {

    @Test
    public void getCharacterCodeIncludesWarlock() {
        assertEquals("Warlock", D2TxtFile.getCharacterCode(7));
    }
}
