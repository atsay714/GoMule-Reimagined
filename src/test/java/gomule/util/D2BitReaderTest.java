package gomule.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class D2BitReaderTest {

    // The "55AA55AA" shared-stash pane marker.
    private static final byte[] MARKER = {0x55, (byte) 0xAA, 0x55, (byte) 0xAA};

    // A real shared stash (ModernSharedStashSoftCoreV2.d2i) whose very last byte happened to equal
    // the marker's first byte (0x55) threw ArrayIndexOutOfBoundsException: findBytes probed
    // filedata[i + j] for the rest of the marker without checking it stayed in bounds. A lone
    // trailing 0x55 (or any partial marker in the last few bytes) must simply not match, not crash.
    @Test
    public void findBytesDoesNotOverrunOnTrailingPartialMatch() {
        // One real marker at index 0, then a lone 0x55 as the final byte.
        byte[] data = {0x55, (byte) 0xAA, 0x55, (byte) 0xAA, 0x00, 0x55};
        int[] hits = new D2BitReader(data).findBytes(MARKER);
        assertArrayEquals(new int[]{0}, hits);
    }

    // A full marker sitting at the very end of the buffer must still be found -- the bound is
    // "must fit", not "must stop early".
    @Test
    public void findBytesFindsMarkerAtBufferEnd() {
        byte[] data = {0x00, 0x00, 0x55, (byte) 0xAA, 0x55, (byte) 0xAA};
        assertArrayEquals(new int[]{2}, new D2BitReader(data).findBytes(MARKER));
    }

    @Test
    public void findNextBytesDoesNotOverrunOnTrailingPartialMatch() {
        byte[] data = {0x00, 0x55, (byte) 0xAA, 0x55, (byte) 0xAA, 0x55};
        D2BitReader reader = new D2BitReader(data);
        assertEquals(1, reader.findNextBytes(MARKER, 0));
        // Searching past the real match finds nothing (and does not overrun the trailing 0x55).
        assertEquals(-1, reader.findNextBytes(MARKER, 2));
    }

    @Test
    public void getNextByteBoundaryInBits() {
        D2BitReader d2BitReader = new D2BitReader(new byte[]{1, 2, 3});
        assertEquals(0, d2BitReader.getNextByteBoundaryInBits());
        d2BitReader.read(1);
        assertEquals(8, d2BitReader.getNextByteBoundaryInBits());
        d2BitReader.read(1);
        assertEquals(8, d2BitReader.getNextByteBoundaryInBits());
        d2BitReader.read(6);
        assertEquals(8, d2BitReader.getNextByteBoundaryInBits());
        d2BitReader.read(1);
        assertEquals(16, d2BitReader.getNextByteBoundaryInBits());
    }
}