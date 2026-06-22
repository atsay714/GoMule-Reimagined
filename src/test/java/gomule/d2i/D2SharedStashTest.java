package gomule.d2i;

import com.google.common.io.BaseEncoding;
import gomule.d2i.D2SharedStash.D2SharedStashPane;
import gomule.item.D2Item;
import gomule.util.D2BitReader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class D2SharedStashTest {

    // getVisibleTabCount() drives how many stash tabs the UI shows. The Reimagined DLC converted
    // its last "stackable stash" tabs to a storage format GoMule can't decode, so they load as
    // trailing incomplete panes (see D2SharedStashReader). The UI hides exactly those -- the panes
    // stay in the model and are saved back byte-for-byte, they're just not shown as item tabs.

    @Test
    public void visibleTabCountHidesTrailingIncompletePanes() {
        // DLC/Modern shape: 5 normal panes + the 2 converted (incomplete) tabs -> 5 shown.
        D2SharedStash stash = stashWith(complete(), complete(), complete(), complete(), complete(), incomplete(), incomplete());
        assertEquals(7, stash.getPanes().size());
        assertEquals(5, stash.getVisibleTabCount());
    }

    @Test
    public void visibleTabCountShowsAllWhenNothingIncomplete() {
        // Non-DLC (3 normal panes) and ordinary all-good stashes are completely untouched.
        assertEquals(3, stashWith(complete(), complete(), complete()).getVisibleTabCount());
        assertEquals(7, stashWith(complete(), complete(), complete(), complete(), complete(), complete(), complete()).getVisibleTabCount());
    }

    @Test
    public void visibleTabCountOnlyHidesTrailingRunNotMiddleIncompletePanes() {
        // The tab strip is positional, so a hidden middle tab would leave a gap; real converted
        // tabs are always trailing. A non-trailing incomplete pane therefore stays visible.
        D2SharedStash stash = stashWith(complete(), incomplete(), complete(), incomplete(), incomplete());
        assertEquals(3, stash.getVisibleTabCount());
    }

    @Test
    public void visibleTabCountNeverHidesEveryTab() {
        assertEquals(1, stashWith(incomplete(), incomplete()).getVisibleTabCount());
    }

    // The "[LOADED PARTIALLY]" warning should fire only for a *visible* tab that failed to load --
    // not for the DLC's hidden converted tabs, which are incomplete by nature. isItemsIncomplete()
    // stays the raw-data truth (any pane incomplete) for callers that need it.

    @Test
    public void noVisibleIncompleteWhenOnlyConvertedTabsAreIncomplete() {
        D2SharedStash stash = stashWith(complete(), complete(), complete(), complete(), complete(), incomplete(), incomplete());
        assertTrue(stash.isItemsIncomplete());          // raw truth: the file does have incomplete panes
        assertFalse(stash.hasVisibleIncompletePane());  // but nothing the user sees is broken
        assertEquals("", stash.getVisibleIncompleteReason());
    }

    @Test
    public void visibleIncompleteWhenANormalTabFailsToLoad() {
        D2SharedStash stash = stashWith(complete(), incompleteBecause("Item 3 of 12 failed to parse"), complete(), incomplete(), incomplete());
        assertTrue(stash.hasVisibleIncompletePane());
        // Only the visible failure is reported -- the hidden converted tabs are excluded.
        assertEquals("Item 3 of 12 failed to parse", stash.getVisibleIncompleteReason());
    }

    @Test
    public void noVisibleIncompleteWhenNothingIncomplete() {
        assertFalse(stashWith(complete(), complete(), complete()).hasVisibleIncompletePane());
    }

    private static D2SharedStashPane complete() {
        return D2SharedStashPane.fromItems(Collections.emptyList(), 0);
    }

    private static D2SharedStashPane incomplete() {
        return incompleteBecause("converted stackable tab");
    }

    private static D2SharedStashPane incompleteBecause(String reason) {
        return D2SharedStashPane.fromItemsPartial(Collections.emptyList(), 0, reason, new byte[]{1});
    }

    private static D2SharedStash stashWith(D2SharedStashPane... panes) {
        return new D2SharedStash("SoftCore.d2i", new ArrayList<>(Arrays.asList(panes)), new byte[0]);
    }

    @Test
    public void testConstruction() {
        List<D2Item> items = Arrays.asList(
                mockItem(0, 0, 5, 5),
                mockItem(9, 9, 1, 1)
        );
        D2Item[][] expectedGrid = new D2Item[16][13];
        expectedGrid[0][0] = items.get(0);
        expectedGrid[1][0] = items.get(0);
        expectedGrid[2][0] = items.get(0);
        expectedGrid[3][0] = items.get(0);
        expectedGrid[4][0] = items.get(0);
        expectedGrid[0][1] = items.get(0);
        expectedGrid[1][1] = items.get(0);
        expectedGrid[2][1] = items.get(0);
        expectedGrid[3][1] = items.get(0);
        expectedGrid[4][1] = items.get(0);
        expectedGrid[0][2] = items.get(0);
        expectedGrid[1][2] = items.get(0);
        expectedGrid[2][2] = items.get(0);
        expectedGrid[3][2] = items.get(0);
        expectedGrid[4][2] = items.get(0);
        expectedGrid[0][3] = items.get(0);
        expectedGrid[1][3] = items.get(0);
        expectedGrid[2][3] = items.get(0);
        expectedGrid[3][3] = items.get(0);
        expectedGrid[4][3] = items.get(0);
        expectedGrid[0][4] = items.get(0);
        expectedGrid[1][4] = items.get(0);
        expectedGrid[2][4] = items.get(0);
        expectedGrid[3][4] = items.get(0);
        expectedGrid[4][4] = items.get(0);
        expectedGrid[9][9] = items.get(1);
        D2SharedStashPane expected = new D2SharedStashPane(items, expectedGrid, 0, null, null);
        assertEquals(expected, D2SharedStashPane.fromItems(items, 0));
    }

    @Test
    public void testOverlappingItems() {
        assertThrows(RuntimeException.class, () -> {
            List<D2Item> items = Arrays.asList(
                    mockItem(0, 0, 5, 5),
                    mockItem(1, 1, 5, 5)
            );
            D2SharedStashPane.fromItems(items, 0);
        });

    }

    @Test
    public void testOutOfBoundsItems() {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
            List<D2Item> items = Collections.singletonList(
                    mockItem(0, 0, 50, 50)
            );
            D2SharedStashPane.fromItems(items, 0);
        });
    }

    @Test
    public void getItemCovering() {
        List<D2Item> items = Arrays.asList(
                mockItem(0, 0, 5, 5),
                mockItem(7, 7, 2, 2));
        D2SharedStashPane d2SharedStashPane = D2SharedStashPane.fromItems(items, 0);
        assertEquals(items.get(0), d2SharedStashPane.getItemCovering(0, 0));
        assertEquals(items.get(0), d2SharedStashPane.getItemCovering(4, 4));
        assertNull(d2SharedStashPane.getItemCovering(5, 4));
        assertEquals(items.get(1), d2SharedStashPane.getItemCovering(7, 8));
        assertNull(d2SharedStashPane.getItemCovering(6, 6));
    }

    @Test
    public void canDropItem() {
        List<D2Item> items = Arrays.asList(
                mockItem(0, 0, 5, 5),
                mockItem(7, 7, 2, 2));
        D2SharedStashPane d2SharedStashPane = D2SharedStashPane.fromItems(items, 0);
        assertFalse(d2SharedStashPane.canDropItem(0, 0, items.get(1)));
        assertTrue(d2SharedStashPane.canDropItem(5, 0, items.get(1)));
        assertFalse(d2SharedStashPane.canDropItem(4, 0, items.get(1)));
        assertFalse(d2SharedStashPane.canDropItem(6, 7, items.get(1)));
        assertFalse(d2SharedStashPane.canDropItem(15, 12, items.get(1)));
        assertFalse(d2SharedStashPane.canDropItem(20, 20, items.get(1)));
    }

    @Test
    public void headerFromBytes() {
        D2BitReader bitReader = new D2BitReader(BaseEncoding.base16().decode("55AA55AA0000000061000000F2A41600070200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
        assertEquals(new D2SharedStash.Header(97, 1484018, 519), D2SharedStash.Header.fromBytes(bitReader));
    }

    private D2Item mockItem(int col, int row, int width, int height) {
        D2Item mock = Mockito.mock(D2Item.class, Mockito.CALLS_REAL_METHODS);
        Mockito.when(mock.get_row()).thenReturn((short) row);
        Mockito.when(mock.get_col()).thenReturn((short) col);
        Mockito.when(mock.get_width()).thenReturn((short) width);
        Mockito.when(mock.get_height()).thenReturn((short) height);
        return mock;
    }
}