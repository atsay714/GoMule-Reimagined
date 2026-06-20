package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash.D2SharedStashPane;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SharedStashPanelTest {

    private static final int TAB_CLICK_Y = 61; // midpoint of TAB_STRIP_CLICK_TOP..TAB_STRIP_CLICK_BOTTOM
    // Midpoint x of each of the 7 possible tab slots, in order.
    private static final int[] TAB_CLICK_X = {57, 118, 181, 243, 306, 368, 431};

    private static D2SharedStashPane completePane() {
        return D2SharedStashPane.fromItems(Collections.emptyList(), 0);
    }

    private static D2SharedStashPane incompletePane() {
        return D2SharedStashPane.fromItemsPartial(Collections.emptyList(), 0, "test", new byte[]{1});
    }

    // A real non-DLC shared stash always has exactly 3 panes and none of them are incomplete (see
    // D2SharedStashReaderTest's thirdRealWorldStashFromNonDlcCharacterLoadsAndSavesFullyWithNoFalsePartialLoad).
    // Tabs 1-3 must stay clickable; tabs 4-7 don't exist and were never clickable before this change.
    @Test
    public void nonDlcStashClicksAllThreeRealTabsAndNothingPastThem() {
        List<D2SharedStashPane> panes = Arrays.asList(completePane(), completePane(), completePane());

        assertEquals(0, SharedStashPanel.getClickedTabIndex(TAB_CLICK_X[0], TAB_CLICK_Y, panes));
        assertEquals(1, SharedStashPanel.getClickedTabIndex(TAB_CLICK_X[1], TAB_CLICK_Y, panes));
        assertEquals(2, SharedStashPanel.getClickedTabIndex(TAB_CLICK_X[2], TAB_CLICK_Y, panes));
        for (int i = 3; i < TAB_CLICK_X.length; i++) {
            assertNull(SharedStashPanel.getClickedTabIndex(TAB_CLICK_X[i], TAB_CLICK_Y, panes));
        }
    }

    // A real DLC shared stash has 7 panes; GoMule can't parse 2 of them (real fixture: panes 5 and
    // 6 -- see D2SharedStashReaderTest's secondRealWorldStashWithConvertedTabsLoadsPartiallyAndSavesTheRestSafely).
    // Their tabs must no longer be clickable, while the 5 real tabs are unaffected.
    @Test
    public void dlcStashHidesOnlyTheIncompleteTabsAndKeepsTheRestClickable() {
        List<D2SharedStashPane> panes = Arrays.asList(
                completePane(), completePane(), completePane(), completePane(), completePane(),
                incompletePane(), incompletePane());

        for (int i = 0; i <= 4; i++) {
            assertEquals(i, SharedStashPanel.getClickedTabIndex(TAB_CLICK_X[i], TAB_CLICK_Y, panes));
        }
        assertNull(SharedStashPanel.getClickedTabIndex(TAB_CLICK_X[5], TAB_CLICK_Y, panes));
        assertNull(SharedStashPanel.getClickedTabIndex(TAB_CLICK_X[6], TAB_CLICK_Y, panes));
    }

    // If only one of the trailing tabs happens to be incomplete, only that one is hidden -- this
    // isn't hardcoded to "always tabs 6 and 7", it follows whichever panes actually failed to parse.
    @Test
    public void onlyTheActuallyIncompletePaneIsHidden() {
        List<D2SharedStashPane> panes = Arrays.asList(
                completePane(), completePane(), completePane(), completePane(), completePane(),
                completePane(), incompletePane());

        assertEquals(5, SharedStashPanel.getClickedTabIndex(TAB_CLICK_X[5], TAB_CLICK_Y, panes));
        assertNull(SharedStashPanel.getClickedTabIndex(TAB_CLICK_X[6], TAB_CLICK_Y, panes));
    }

    @Test
    public void clicksOutsideTheTabStripRowAreIgnoredRegardlessOfPanes() {
        List<D2SharedStashPane> panes = Collections.singletonList(completePane());

        assertNull(SharedStashPanel.getClickedTabIndex(TAB_CLICK_X[0], 50, panes)); // just above the strip
        assertNull(SharedStashPanel.getClickedTabIndex(TAB_CLICK_X[0], 73, panes)); // just below the strip
    }
}
