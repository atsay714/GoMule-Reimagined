package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash;
import gomule.gui.D2FileManager;
import gomule.gui.D2ImageCache;
import gomule.item.D2Item;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

import static java.util.Collections.emptyList;

public class SharedStashPanel extends JPanel {

    public static final int BG_WIDTH = 552;
    public static final int BG_HEIGHT = 567;

    // Right edge (in panel-local pixels) of each of the up to 7 stash tabs baked into
    // resources/stash1.png..stash7.png's tab strip -- shared between click detection (below) and
    // build()'s blanking-out of tabs for panes GoMule can't parse (see DLC tabs comment in build()).
    // The strip's left edge starts at TAB_STRIP_LEFT; tab i spans
    // (i == 0 ? TAB_STRIP_LEFT : TAB_RIGHT_EDGES[i - 1]) .. TAB_RIGHT_EDGES[i].
    public static final int[] TAB_RIGHT_EDGES = {87, 150, 212, 275, 337, 400, 462};
    public static final int TAB_STRIP_LEFT = 27;
    public static final int TAB_STRIP_CLICK_TOP = 51;
    public static final int TAB_STRIP_CLICK_BOTTOM = 72;

    // Region just past the last real tab, before the panel's right border -- a plain dark texture
    // with the same continuous gold underline as the tabs themselves, and never affected by which
    // tab is highlighted. Used as a source to paint over (DLC-only) tabs GoMule can't parse, since
    // there's no art with fewer than 7 tabs baked in. Sampled fresh from whichever stash*.png is
    // currently being drawn (not a separately loaded reference image) so any per-image texture
    // noise matches its neighbours exactly.
    private static final int BLANK_TAB_PATCH_TOP = 50;
    private static final int BLANK_TAB_PATCH_BOTTOM = 75;
    private static final int BLANK_TAB_PATCH_SOURCE_LEFT = 465;
    private static final int BLANK_TAB_PATCH_SOURCE_RIGHT = 521;

    private final D2FileManager fileManager;
    private final D2ViewSharedStash sharedStashView;
    private int selectedStashPaneIndex = 0;
    private Image background;

    public SharedStashPanel(D2FileManager fileManager, D2ViewSharedStash sharedStashView) {
        this.fileManager = fileManager;
        this.sharedStashView = sharedStashView;
        setLayout(new BorderLayout());
        setSize(BG_WIDTH, BG_HEIGHT);
        Dimension lSize = new Dimension(BG_WIDTH, BG_HEIGHT);
        setPreferredSize(lSize);
        addMouseListener(new SharedStashPanelMouseClickHandler(this));
        addMouseMotionListener(new SharedStashMouseMotionListener(this));
        setVisible(true);
        ToolTipManager.sharedInstance().setDismissDelay(40000);
        ToolTipManager.sharedInstance().setInitialDelay(300);
        build();
    }

    public void build() {
        Image lEmptyBackground = D2ImageCache.getImage("stash" + (selectedStashPaneIndex + 1) + ".png");
        background = fileManager.getGraphicsConfiguration().createCompatibleImage(BG_WIDTH, BG_HEIGHT, Transparency.BITMASK);
        Graphics2D lGraphics = (Graphics2D) background.getGraphics();
        lGraphics.drawImage(lEmptyBackground, 0, 0, this);
        hideIncompleteTabs(lGraphics, lEmptyBackground);
        if (getSharedStash() != null) placeItemsInView();
        repaint();
    }

    // DLC stash files have up to 4 extra tabs (gems/runes/materials-type categories) whose item
    // lists GoMule can't parse at all -- D2SharedStashReader preserves their exact original bytes
    // so the file still saves correctly (see D2SharedStash.isItemsIncomplete()'s comment), but
    // their in-memory item list is empty. Leaving their tabs clickable would show what looks like
    // an empty page -- indistinguishable from "my items are gone" -- for items that are actually
    // still safely there, just unparsed. Non-DLC stashes never have an incomplete pane (confirmed
    // against a real non-DLC fixture -- see D2SharedStashReaderTest), so this never touches their
    // tabs. Painted over rather than skipped when drawing, since the tab art is baked into the
    // background image with no "N tabs" variant to fall back to.
    private void hideIncompleteTabs(Graphics2D lGraphics, Image lEmptyBackground) {
        D2SharedStash sharedStash = getSharedStash();
        if (sharedStash == null) return;
        java.util.List<D2SharedStash.D2SharedStashPane> panes = sharedStash.getPanes();
        for (int i = 0; i < panes.size(); i++) {
            if (panes.get(i).isIncomplete()) paintOverTab(lGraphics, lEmptyBackground, i);
        }
    }

    private void paintOverTab(Graphics2D lGraphics, Image lEmptyBackground, int tabIndex) {
        int left = (tabIndex == 0) ? TAB_STRIP_LEFT : TAB_RIGHT_EDGES[tabIndex - 1];
        int right = TAB_RIGHT_EDGES[tabIndex];
        int patchWidth = BLANK_TAB_PATCH_SOURCE_RIGHT - BLANK_TAB_PATCH_SOURCE_LEFT;
        for (int x = left; x < right; x += patchWidth) {
            int width = Math.min(patchWidth, right - x);
            lGraphics.drawImage(lEmptyBackground,
                    x, BLANK_TAB_PATCH_TOP, x + width, BLANK_TAB_PATCH_BOTTOM,
                    BLANK_TAB_PATCH_SOURCE_LEFT, BLANK_TAB_PATCH_TOP, BLANK_TAB_PATCH_SOURCE_LEFT + width, BLANK_TAB_PATCH_BOTTOM,
                    this);
        }
    }

    // Maps a click's x-coordinate to which of the up to 7 baked-in tabs it falls within, ignoring
    // whether a pane actually exists/is clickable at that index -- see getClickedTabIndex(), which
    // combines this with the real pane list to decide whether the click does anything.
    private static Integer getTabIndexForXCoord(int x) {
        if (x < TAB_STRIP_LEFT) return null;
        for (int i = 0; i < TAB_RIGHT_EDGES.length; i++) {
            if (x <= TAB_RIGHT_EDGES[i]) return i;
        }
        return null;
    }

    // Which tab (if any) a click at (x, y) selects, given this stash's actual panes -- null if the
    // click misses the tab strip entirely, lands past the last real pane, or lands on a pane
    // GoMule can't parse (painted over in build(); see hideIncompleteTabs()'s comment). Kept as a
    // pure function of (x, y, panes), independent of any live SharedStashPanel/Swing state, so the
    // tab-hiding decision is directly testable.
    public static Integer getClickedTabIndex(int x, int y, java.util.List<D2SharedStash.D2SharedStashPane> panes) {
        if (y < TAB_STRIP_CLICK_TOP || y > TAB_STRIP_CLICK_BOTTOM) return null;
        Integer tabIndex = getTabIndexForXCoord(x);
        if (tabIndex == null || tabIndex >= panes.size() || panes.get(tabIndex).isIncomplete()) return null;
        return tabIndex;
    }

    private void placeItemsInView() {
        D2SharedStash.D2SharedStashPane pane = getSelectedStashPane();
        pane.getItems().forEach(item -> {
            if (item.get_location() != 0 && item.get_body_position() != 0 && item.get_panel() != 5) return;
            Image image = D2ImageCache.getDC6Image(item);
            int col = item.get_col();
            int row = item.get_row();
            int x = getXCoordForCol(col);
            int y = getYCoordForRow(row);
            background.getGraphics().drawImage(image, x, y, this);
        });
        background.getGraphics().drawString(Long.toString(pane.getGold()), 248, 516);
    }

    public static int getXCoordForCol(int col) {
        int diffx = (col / 2);
        return (int)(30 + (col * 28.4) + ((diffx * 3) + ((col - diffx) * 2)));
    }

    public static int getYCoordForRow(int row) {
        int diffy = (row / 2);
        return (int)(77 + (row * 28.4) + ((diffy * 3) + ((row - diffy) * 2)));
    }

    public static int getColForXCoord(int x) {
        if (x < 29) return -1;
        return ((2 * x) - 58) / 61;
    }

    public static int getRowForYCoord(int y) {
        if (y < 75) return -1;
        return ((2 * y) - 150) / 61;
    }

    @Override
    public void paint(Graphics pGraphics) {
        super.paint(pGraphics);
        Graphics2D lGraphics = (Graphics2D) pGraphics;
        lGraphics.drawImage(background, 0, 0, this);
    }

    public void setCursorPickupItem() {
        setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    public void setCursorDropItem() {
        setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
    }

    public void setCursorNormal() {
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    public java.util.List<D2Item> removeAllItems() {
        D2SharedStash sharedStash = getSharedStash();
        if (sharedStash == null) return emptyList();
        D2SharedStash.D2SharedStashPane stashPane = getSelectedStashPane();
        sharedStash.replacePane(selectedStashPaneIndex, D2SharedStash.D2SharedStashPane.fromItems(emptyList(), stashPane.getGold()));
        sharedStash.setModified(true);
        return stashPane.getItems();
    }

    public java.util.List<D2Item> tryToAddItems(java.util.List<D2Item> items) {
        D2SharedStash sharedStash = getSharedStash();
        if (sharedStash == null) return emptyList();
        D2SharedStash.D2SharedStashPane stashPane = getSelectedStashPane();
        java.util.List<D2Item> successfullyAddedItems = new ArrayList<>();
        for (D2Item item : items) {
            stashPane = getD2SharedStashPane(stashPane, successfullyAddedItems, item);
        }
        sharedStash.replacePane(selectedStashPaneIndex, stashPane);
        sharedStash.setModified(true);
        return successfullyAddedItems;
    }

    public D2SharedStash.D2SharedStashPane getSelectedStashPane() {
        return getSharedStash().getPane(selectedStashPaneIndex);
    }

    public D2SharedStash getSharedStash() {
        return sharedStashView.getSharedStash();
    }

    public int getSelectedStashPaneIndex() {
        return selectedStashPaneIndex;
    }

    public void setSelectedStashPaneIndex(int selectedStashPaneIndex) {
        this.selectedStashPaneIndex = selectedStashPaneIndex;
    }

    private D2SharedStash.D2SharedStashPane getD2SharedStashPane(D2SharedStash.D2SharedStashPane stashPane, java.util.List<D2Item> successfullyAddedItems, D2Item item) {
        for (int i = 0; i < 13; i++) {
            for (int j = 0; j < 16; j++) {
                if (stashPane.canDropItem(j, i, item)) {
                    stashPane = stashPane.addItem(j, i, item);
                    successfullyAddedItems.add(item);
                    return stashPane;
                }
            }
        }
        return stashPane;
    }
}