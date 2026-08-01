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
 *  - noconsume: stat ID 205 (item_noconsume) exists only as a properties.txt
 *    definition in the current data — no item or affix actually uses it yet.
 *    The test value (50) is a representative value chosen to verify the rendering
 *    path works; it is NOT derived from any real item row. This is analogous to
 *    the affix-rand situation documented below.
 *  - war-tab-rand: properties.txt row 282, resolves item_addskill_tab to the mod's
 *    Warlock class tabs (Demon Skills, Eldritch Skills, Chaos Skills). properties.txt
 *    lists these as val 21/22/23, but that is the generation-time sequential tab
 *    numbering; what an item actually STORES and what D2Prop renders is the global
 *    "class * 8 + tab" index, so for the Warlock (class 7) those tabs are 56/57/58
 *    (see getSkillTree()'s comment and the real-charm test in D2SharedStashReaderTest).
 *    Also exercises a known-good vanilla tab to confirm the base path works.
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
    // stat ID 205 = item_noconsume. This stat exists only as a properties.txt
    // definition in the current data — no item or affix references it yet.
    // Value 50 is a representative value used to exercise the rendering path;
    // it is NOT derived from a real item row. (Compare: affix-rand below, which
    // is similarly untestable via generateDisplay() for different reasons.)
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
    // tab indices 21 (Demon Skills), 22 (Eldritch Skills), 23 (Chaos Skills) —
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

    // The three Warlock tabs use the GLOBAL "class * 8 + tab" index that items actually store
    // (class 7 -> 56/57/58), not the sequential 21/22/23 that properties.txt's war-tab-rand lists.
    // A real Warlock grand charm stores tab 58 (Chaos Skills) -- see the end-to-end test in
    // D2SharedStashReaderTest that reads it straight from a real shared stash.
    @Test
    void warTabRandDemonSkillsRendersNonEmpty() {
        // Warlock tab 56 = Demon Skills (StrSkillTab1 = StrSklTabItem24 = "+%d to Demon Skills")
        D2Prop prop = new D2Prop(188, new int[]{56, 1, 0}, QFLAG_STANDARD);
        String rendered = prop.generateDisplay(QFLAG_STANDARD, CLVL_DONT_CARE);

        assertNotNull(rendered, "item_addskill_tab (tree 56) must not return null");
        assertFalse(rendered.isEmpty(), "item_addskill_tab (tree 56) must not return empty");
        assertFalse(rendered.contains("Unknown Tree"),
                "item_addskill_tab (tree 56) must not fall through to Unknown Tree; got: " + rendered);
        assertTrue(rendered.contains("Demon Skills (Warlock Only)"),
                "Expected 'Demon Skills (Warlock Only)' in rendered text for tree 56 but got: " + rendered);
    }

    @Test
    void warTabRandEldritchRendersNonEmpty() {
        // Warlock tab 57 = Eldritch Skills (StrSkillTab2 = StrSklTabItem22 = "+%d to Eldritch Skills")
        D2Prop prop = new D2Prop(188, new int[]{57, 1, 0}, QFLAG_STANDARD);
        String rendered = prop.generateDisplay(QFLAG_STANDARD, CLVL_DONT_CARE);

        assertNotNull(rendered, "item_addskill_tab (tree 57) must not return null");
        assertFalse(rendered.isEmpty(), "item_addskill_tab (tree 57) must not return empty");
        assertFalse(rendered.contains("Unknown Tree"),
                "item_addskill_tab (tree 57) must not fall through to Unknown Tree; got: " + rendered);
        assertTrue(rendered.contains("Eldritch Skills (Warlock Only)"),
                "Expected 'Eldritch Skills (Warlock Only)' in rendered text for tree 57 but got: " + rendered);
    }

    @Test
    void warTabRandChaosRendersNonEmpty() {
        // Warlock tab 58 = Chaos Skills (StrSkillTab3 = StrSklTabItem23 = "+%d to Chaos Skills").
        // This is the exact value a real Warlock grand charm stores (the one that used to render
        // "+1 to Unknown Tree (P 188)").
        D2Prop prop = new D2Prop(188, new int[]{58, 1, 0}, QFLAG_STANDARD);
        String rendered = prop.generateDisplay(QFLAG_STANDARD, CLVL_DONT_CARE);

        assertNotNull(rendered, "item_addskill_tab (tree 58) must not return null");
        assertFalse(rendered.isEmpty(), "item_addskill_tab (tree 58) must not return empty");
        assertFalse(rendered.contains("Unknown Tree"),
                "item_addskill_tab (tree 58) must not fall through to Unknown Tree; got: " + rendered);
        assertTrue(rendered.contains("Chaos Skills (Warlock Only)"),
                "Expected 'Chaos Skills (Warlock Only)' in rendered text for tree 58 but got: " + rendered);
    }
}
