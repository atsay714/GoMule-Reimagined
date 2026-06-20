package gomule.d2i;

import com.google.common.io.BaseEncoding;
import com.google.common.io.Resources;
import gomule.item.D2ItemRenderer;
import gomule.util.D2BitReader;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

public class D2SharedStashReaderTest {

    @Test
    public void simpleStash() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        byte[] simpleStash = BaseEncoding.base16()
                .decode(
                        "55AA55AA0000000063000000F2A416004D00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D01001000A2000564D6900855AA55AA0000000063000000000000004D00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D01001000A2000564F6472255AA55AA0000000063000000000000004400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D0000");
        D2SharedStash stash =
                new D2SharedStashReader().readStash("somethingSoftCore.d2i", new D2BitReader(simpleStash));
        assertEquals(1484018, stash.getPane(0).getGold());
        assertEquals(0, stash.getPane(1).getGold());
        assertEquals(0, stash.getPane(2).getGold());
        assertTrue(stash.isSC());
        assertFalse(stash.isHC());
        assertEquals(
                singletonList("TP\n" + "Version: Resurrected\n"), getItemDumps(stash.getPane(0)));
        assertEquals(singletonList("ID\n" + "Version: Resurrected\n"), getItemDumps(stash.getPane(1)));
        assertEquals(emptyList(), getItemDumps(stash.getPane(2)));
        StringWriter out = new StringWriter();
        stash.fullDump(new PrintWriter(out));
        assertEquals(
                "somethingSoftCore.d2i\n" + "\n"
                        + "\n"
                        + "TP\n"
                        + "Version: Resurrected\n"
                        + "\n"
                        + "ID\n"
                        + "Version: Resurrected\n"
                        + "Finished: somethingSoftCore.d2i\n\n",
                out.toString().replaceAll("\r", ""));
    }

    @Test
    public void issue1RealWorldStashLoadsWithoutDisconnecting() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        String filename = new java.io.File(
                Resources.getResource("sharedStash/SharedStashSoftCoreV2.d2i").toURI())
                .getAbsolutePath();
        D2SharedStash stash = new D2SharedStashReader().readStash(filename);

        assertEquals(7, stash.getPanes().size());
        assertEquals(2500000, stash.getPane(0).getGold());
        assertEquals(2500000, stash.getPane(1).getGold());
        assertEquals(957236, stash.getPane(2).getGold());
        assertEquals(0, stash.getPane(3).getGold());
        assertEquals(0, stash.getPane(4).getGold());
        assertEquals(0, stash.getPane(5).getGold());
        assertEquals(0, stash.getPane(6).getGold());
        assertEquals(82, stash.getPane(0).getItems().size());
        assertEquals(23, stash.getPane(1).getItems().size());
        assertEquals(22, stash.getPane(2).getItems().size());
        assertEquals(17, stash.getPane(3).getItems().size());
        assertEquals(42, stash.getPane(4).getItems().size());
        assertEquals(34, stash.getPane(5).getItems().size());
        assertEquals(64, stash.getPane(6).getItems().size());
    }

    // A second real shared stash, from the same Reimagined mod patch that converted two of its
    // tabs to hold runes/keys/gems (see the user's report). Panes 0-4 are completely ordinary and
    // exercised four more real item-format gaps independently of that conversion -- a set item
    // ("Death Knight's Demon Blade") with a fixed, never-rolled threshold-bonus property
    // (D2Item.needsStoredBaseValue()'s comment), the same flag-29 quirk fixed earlier needing a
    // different trailing-bit count depending on whether the skill it grants is randomly picked
    // or just named (D2Item.hasRandomlyPickedSkillProperty()'s comment), and -- the deciding
    // finding -- proof that the long-standing "socketed XOR ethereal" trailing-bit rule was wrong
    // the whole time: every character file that validated it happened to have its 1-bit error
    // masked by D2Item's byte-rounding, and a real unique armor here ("Adamantine Mail") exposed
    // it by landing on a byte boundary that didn't absorb the error. The real rule is simpler:
    // always exactly 1 trailing bit, never 2, regardless of either flag.
    // Pane 5 (one of the two converted tabs) decodes 12 ordinary loose items -- potions, a
    // Reimagined-specific consumable, and several runes, none of which are stored as regular
    // items at all and needed their own fixes (D2Item.isLooseRuneOrGem's comment et al.) -- before
    // hitting content that isn't stored as ordinary items at all: what look like loose gems turn
    // out to be false-positive decodes once checked by decoding several items in a row instead of
    // just the next one (most likely the same kind of simplified, non-item storage the Gem Bag
    // uses, but not yet confirmed). Pane 6 is the second converted tab and fails on its very first
    // item. D2SharedStashReader's partial-load fallback (mirroring D2Character's) keeps both as
    // empty, clearly-marked-incomplete panes instead of losing every pane, including the five
    // perfectly good ones.
    @Test
    public void secondRealWorldStashWithConvertedTabsLoadsPartially() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        String filename = new java.io.File(
                Resources.getResource("sharedStash/ModernSharedStashSoftCoreV2.d2i").toURI())
                .getAbsolutePath();
        D2SharedStash stash = new D2SharedStashReader().readStash(filename);

        assertEquals(7, stash.getPanes().size());
        assertTrue(stash.isItemsIncomplete());

        assertEquals(45, stash.getPane(0).getItems().size());
        assertEquals(850000, stash.getPane(0).getGold());
        assertEquals(50, stash.getPane(1).getItems().size());
        assertEquals(46, stash.getPane(2).getItems().size());
        assertEquals(44, stash.getPane(3).getItems().size());
        assertEquals(32, stash.getPane(4).getItems().size());
        for (int i = 0; i <= 4; i++) {
            assertFalse(stash.getPane(i).isIncomplete(), "pane " + i + " should have loaded completely");
        }

        assertTrue(stash.getPane(5).isIncomplete());
        assertEquals(0, stash.getPane(5).getItems().size());
        assertTrue(stash.getPane(6).isIncomplete());
        assertEquals(0, stash.getPane(6).getItems().size());

        Exception saveException = assertThrows(RuntimeException.class, () -> stash.saveInternal(null));
        assertTrue(saveException.getMessage().contains("did not fully load"));
    }

    private List<String> getItemDumps(D2SharedStash.D2SharedStashPane pane) {
        return pane.getItems().stream()
                .map(it -> D2ItemRenderer.itemDump(it, true).replace("\r", ""))
                .collect(Collectors.toList());
    }
}
