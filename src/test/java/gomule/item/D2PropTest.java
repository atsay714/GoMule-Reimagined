package gomule.item;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests verifying that the four property codes newly-active in v3.0.10
 * (pierce-immunity-fire, noconsume, war-tab-rand, affix-rand) render through
 * D2Prop.generateDisplay() without leaking raw stat codes, empty strings, or
 * fallback text.
 *
 * Data sources:
 *  - pierce-immunity-fire: uniqueitems.txt row "Flame Rift" (ID 402), value 300
 *  - noconsume: magicsuffix.txt row "of Mosaic", values 50/50
 *  - war-tab-rand: properties.txt row 282, resolves item_addskill_tab with
 *    tab indices 21/22/23 (Warlock class tabs: Martial Arts, Eldritch Skills,
 *    Chaos Skills); also exercises known-good tabs to confirm the base path works
 *  - affix-rand: properties.txt row 280, func1=25, stat1 empty — this property
 *    generates a random affix at item creation time and stores the resulting affix
 *    stats (not affix-rand itself) in the save; there is therefore no D2Prop stat
 *    to render, so it is not verifiable via generateDisplay(). That is expected
 *    and documented here as a deliberate non-test.
 *
 * Stat IDs (from ItemStatCost.txt *ID column, used as D2Prop pNum):
 *   188 = item_addskill_tab   (descfunc 14)
 *   189 = item_pierce_fire_immunity  (descfunc 19)
 *   205 = item_noconsume             (descfunc 19)
 */
public class D2PropTest {

    // qFlag 0 = standard (non-set) property; matches the rendering path an
    // ordinary unique/magic item takes through D2PropCollection.
    private static final int QFLAG_STANDARD = 0;
    private static final int CLVL_DONT_CARE = 1;

    @BeforeAll
    static void loadData() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    // -------------------------------------------------------------------------
    // pierce-immunity-fire  (descfunc 19 path, PROPS tooltip lookup)
    // Value 300 taken from uniqueitems.txt "Flame Rift" (and PreCrafted Flame Rift).
    // -------------------------------------------------------------------------

    @Test
    void pierceImmunityFireRendersRealText() {
        // stat ID 189 = item_pierce_fire_immunity
        D2Prop prop = new D2Prop(189, new int[]{300, 0, 0}, QFLAG_STANDARD);
        String rendered = prop.generateDisplay(QFLAG_STANDARD, CLVL_DONT_CARE);

        assertNotNull(rendered, "pierce-immunity-fire must not return null");
        assertFalse(rendered.isEmpty(), "pierce-immunity-fire must not return empty string");
        assertFalse(rendered.contains("item_pierce_fire_immunity"),
                "pierce-immunity-fire must not leak the raw stat code");
        assertFalse(rendered.equals("???"),
                "pierce-immunity-fire must not produce '???' fallback");
        // Properties.txt *Tooltip: "Monster Fire Immunity is Sundered"
        // The color codes (ÿc4...ÿc3) come from the translation, not this code path.
        // The PROPS tooltip is used directly, which has no color codes.
        assertTrue(rendered.contains("Monster Fire Immunity is Sundered"),
                "Expected 'Monster Fire Immunity is Sundered' but got: " + rendered);
    }

    // -------------------------------------------------------------------------
    // noconsume  (descfunc 19 path, PROPS tooltip lookup)
    // Value 50 taken from magicsuffix.txt "of Mosaic" (charge-noconsume stat
    // with min=50/max=50); noconsume property itself uses the same stat with
    // the same 50/50 range (also confirmed in magicsuffix.txt row 733).
    // -------------------------------------------------------------------------

    @Test
    void noconsumeRendersRealText() {
        // stat ID 205 = item_noconsume
        D2Prop prop = new D2Prop(205, new int[]{50, 0, 0}, QFLAG_STANDARD);
        String rendered = prop.generateDisplay(QFLAG_STANDARD, CLVL_DONT_CARE);

        assertNotNull(rendered, "noconsume must not return null");
        assertFalse(rendered.isEmpty(), "noconsume must not return empty string");
        assertFalse(rendered.contains("item_noconsume"),
                "noconsume must not leak the raw stat code");
        assertFalse(rendered.equals("???"),
                "noconsume must not produce '???' fallback");
        // Properties.txt *Tooltip: "+# to not Consume Quantity"
        assertTrue(rendered.contains("not Consume Quantity"),
                "Expected 'not Consume Quantity' in rendered text but got: " + rendered);
        assertTrue(rendered.contains("50"),
                "Expected value '50' in rendered text but got: " + rendered);
    }

    // -------------------------------------------------------------------------
    // war-tab-rand  (descfunc 14 path via item_addskill_tab, tree index lookup)
    //
    // The property randomly picks one of three item_addskill_tab stats with
    // tab indices 21 (Martial Arts), 22 (Eldritch Skills), 23 (Chaos Skills) —
    // these are the new Warlock class skill trees introduced by the mod.
    //
    // Known-good tree (34 = Warcry Skills, Barbarian) is tested first to confirm
    // the base descfunc-14 path works, then the three Warlock tabs are tested.
    // -------------------------------------------------------------------------

    @Test
    void skillTabKnownTreeRendersCorrectly() {
        // Tree 34 = Warcry Skills (Barbarian Only) — present in vanilla D2
        // pVals: [tab_index, level_bonus, ...]
        D2Prop prop = new D2Prop(188, new int[]{34, 1, 0}, QFLAG_STANDARD);
        String rendered = prop.generateDisplay(QFLAG_STANDARD, CLVL_DONT_CARE);

        assertNotNull(rendered, "item_addskill_tab (tree 34) must not return null");
        assertFalse(rendered.isEmpty(), "item_addskill_tab (tree 34) must not return empty");
        assertEquals("+1 to Warcry Skills (Barbarian Only)", rendered,
                "item_addskill_tab (tree 34) did not render correctly");
    }

    @Test
    void warTabRandMartialArtsRendersNonEmpty() {
        // war-tab-rand tab index 21 = Martial Arts (Warlock Only)
        D2Prop prop = new D2Prop(188, new int[]{21, 1, 0}, QFLAG_STANDARD);
        String rendered = prop.generateDisplay(QFLAG_STANDARD, CLVL_DONT_CARE);

        assertNotNull(rendered, "item_addskill_tab (tree 21) must not return null");
        assertFalse(rendered.isEmpty(), "item_addskill_tab (tree 21) must not return empty");
        assertFalse(rendered.contains("Unknown Tree"),
                "item_addskill_tab (tree 21) must not fall through to Unknown Tree; got: " + rendered);
        assertTrue(rendered.contains("Martial Arts (Warlock Only)"),
                "Expected 'Martial Arts (Warlock Only)' in rendered text for tree 21 but got: " + rendered);
    }

    @Test
    void warTabRandEldritchRendersNonEmpty() {
        // war-tab-rand tab index 22 = Eldritch Skills (Warlock Only)
        D2Prop prop = new D2Prop(188, new int[]{22, 1, 0}, QFLAG_STANDARD);
        String rendered = prop.generateDisplay(QFLAG_STANDARD, CLVL_DONT_CARE);

        assertNotNull(rendered, "item_addskill_tab (tree 22) must not return null");
        assertFalse(rendered.isEmpty(), "item_addskill_tab (tree 22) must not return empty");
        assertFalse(rendered.contains("Unknown Tree"),
                "item_addskill_tab (tree 22) must not fall through to Unknown Tree; got: " + rendered);
        assertTrue(rendered.contains("Eldritch Skills (Warlock Only)"),
                "Expected 'Eldritch Skills (Warlock Only)' in rendered text for tree 22 but got: " + rendered);
    }

    @Test
    void warTabRandChaosRendersNonEmpty() {
        // war-tab-rand tab index 23 = Chaos Skills (Warlock Only)
        D2Prop prop = new D2Prop(188, new int[]{23, 1, 0}, QFLAG_STANDARD);
        String rendered = prop.generateDisplay(QFLAG_STANDARD, CLVL_DONT_CARE);

        assertNotNull(rendered, "item_addskill_tab (tree 23) must not return null");
        assertFalse(rendered.isEmpty(), "item_addskill_tab (tree 23) must not return empty");
        assertFalse(rendered.contains("Unknown Tree"),
                "item_addskill_tab (tree 23) must not fall through to Unknown Tree; got: " + rendered);
        assertTrue(rendered.contains("Chaos Skills (Warlock Only)"),
                "Expected 'Chaos Skills (Warlock Only)' in rendered text for tree 23 but got: " + rendered);
    }
}
