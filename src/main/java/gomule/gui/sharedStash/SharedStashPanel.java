package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash;
import gomule.gui.D2FileManager;
import gomule.gui.D2ImageCache;
import gomule.item.D2Item;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

public class SharedStashPanel extends JPanel {

    public static final int BG_WIDTH = 552;
    public static final int BG_HEIGHT = 567;

    // Left x of each of the 7 tab slots, plus the strip's right edge as the final entry; matches
    // the click regions in SharedStashPanelMouseClickHandler.getPossibleStashTabClick().
    private static final int[] TAB_X_BOUNDS = {27, 87, 150, 212, 275, 337, 400, 462};
    private static final int TAB_BAND_TOP = 46;     // inclusive; top of the tab buttons' band
    private static final int TAB_BAND_BOTTOM = 75;  // exclusive; bottom of that band
    // A column of empty recessed frame just right of the tab strip, used as the "no tab" texture.
    private static final int RECESS_SAMPLE_X = 503;
    // The baked strip has 7 tab slots. With more visible tabs (a D2RLoader stash can have up to
    // 1000) the strip shows one page of 7 at a time, with the tab numbers painted over the
    // "Shared" labels; D2ViewSharedStash's tab selector bar pages through and jumps between them.
    public static final int TABS_PER_PAGE = TAB_X_BOUNDS.length - 1;
    private static final int TAB_LABEL_TOP = 55;    // inclusive; band holding the "Shared" label
    private static final int TAB_LABEL_BOTTOM = 68; // exclusive
    private final D2FileManager fileManager;
    private final D2ViewSharedStash sharedStashView;
    // Position among the *visible* tabs (see D2SharedStash.getVisiblePaneIndices()), not a pane
    // index: the DLC's hidden converted tabs can sit between visible ones.
    private int selectedTab = 0;
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
        // A tab that no longer exists must never stay selected -- e.g. after reconnecting to a
        // stash with fewer tabs. Fall back to the first (always-visible) tab.
        if (getSharedStash() != null && selectedTab >= getVisibleTabCount()) {
            selectedTab = 0;
        }
        Image lEmptyBackground = D2ImageCache.getImage("stash" + (selectedTab % TABS_PER_PAGE + 1) + ".png");
        background = fileManager.getGraphicsConfiguration().createCompatibleImage(BG_WIDTH, BG_HEIGHT, Transparency.BITMASK);
        Graphics2D lGraphics = (Graphics2D) background.getGraphics();
        lGraphics.drawImage(lEmptyBackground, 0, 0, this);
        if (getSharedStash() != null) {
            maskHiddenTabs();
            labelTabsWithNumbers();
            placeItemsInView();
        }
        repaint();
    }

    // How many tabs to show -- the model hides the DLC's converted "stackable stash" tabs (see
    // D2SharedStash.getVisiblePaneIndices()). Returns 0 only when there's no stash connected.
    public int getVisibleTabCount() {
        D2SharedStash sharedStash = getSharedStash();
        return sharedStash == null ? 0 : sharedStash.getVisibleTabCount();
    }

    // First visible tab shown in the strip's current page of TABS_PER_PAGE.
    public int getPageStart() {
        return selectedTab - selectedTab % TABS_PER_PAGE;
    }

    // Number of tabs in the strip's current page -- TABS_PER_PAGE except on a short last page.
    public int getTabsOnPage() {
        return Math.min(TABS_PER_PAGE, getVisibleTabCount() - getPageStart());
    }

    // Each stashN.png bakes in all 7 "Shared" tab buttons, so the unused ones (past the visible tab
    // count, or past the end of a short last page) are painted over with the empty recessed-frame
    // texture sampled from just right of the strip -- making it look like the stash natively has
    // only that many tabs. Purely cosmetic; no item or pane data is touched.
    private void maskHiddenTabs() {
        int tabsOnPage = getTabsOnPage();
        if (tabsOnPage >= TABS_PER_PAGE) return; // all 7 slots used -> nothing to hide
        if (!(background instanceof BufferedImage)) return;
        BufferedImage image = (BufferedImage) background;
        int xStart = TAB_X_BOUNDS[tabsOnPage];
        int xEnd = TAB_X_BOUNDS[TAB_X_BOUNDS.length - 1];
        for (int y = TAB_BAND_TOP; y < TAB_BAND_BOTTOM; y++) {
            int recessColor = image.getRGB(RECESS_SAMPLE_X, y);
            for (int x = xStart; x < xEnd; x++) {
                image.setRGB(x, y, recessColor);
            }
        }
    }

    // With more tabs than fit in the strip, every slot says "Shared", so paint the tab's number over
    // that label: the label band is filled with the tab's own background colour (sampled per row
    // from just inside its left edge) and the 1-based tab number drawn centred on top. Left alone
    // for 7 or fewer tabs, where the strip already looks exactly like the game's.
    private void labelTabsWithNumbers() {
        if (getVisibleTabCount() <= TABS_PER_PAGE) return;
        if (!(background instanceof BufferedImage)) return;
        BufferedImage image = (BufferedImage) background;
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
        FontMetrics metrics = g.getFontMetrics();
        int pageStart = getPageStart();
        for (int slot = 0; slot < getTabsOnPage(); slot++) {
            int xStart = TAB_X_BOUNDS[slot] + 4;
            int xEnd = TAB_X_BOUNDS[slot + 1] - 6;
            for (int y = TAB_LABEL_TOP; y < TAB_LABEL_BOTTOM; y++) {
                int fill = image.getRGB(xStart, y);
                for (int x = xStart + 1; x < xEnd; x++) image.setRGB(x, y, fill);
            }
            String label = Integer.toString(pageStart + slot + 1);
            int textX = (xStart + xEnd - metrics.stringWidth(label)) / 2;
            int textY = (TAB_LABEL_TOP + TAB_LABEL_BOTTOM + metrics.getAscent() - metrics.getDescent()) / 2;
            g.setColor(pageStart + slot == selectedTab ? new Color(235, 225, 190) : new Color(170, 155, 110));
            g.drawString(label, textX, textY);
        }
        g.dispose();
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
        sharedStash.replacePane(getSelectedStashPaneIndex(), D2SharedStash.D2SharedStashPane.fromItems(emptyList(), stashPane.getGold()));
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
        sharedStash.replacePane(getSelectedStashPaneIndex(), stashPane);
        sharedStash.setModified(true);
        return successfullyAddedItems;
    }

    public D2SharedStash.D2SharedStashPane getSelectedStashPane() {
        return getSharedStash().getPane(getSelectedStashPaneIndex());
    }

    public D2SharedStash getSharedStash() {
        return sharedStashView.getSharedStash();
    }

    // The selected tab's index into D2SharedStash.getPanes() (what replacePane() takes).
    public int getSelectedStashPaneIndex() {
        List<Integer> visiblePanes = getSharedStash().getVisiblePaneIndices();
        return visiblePanes.get(Math.min(selectedTab, visiblePanes.size() - 1));
    }

    public int getSelectedTab() {
        return selectedTab;
    }

    // Selects a visible tab (clamped to the valid range), redraws, and lets the view's tab selector
    // bar follow along.
    public void selectTab(int tab) {
        int clamped = Math.max(0, Math.min(tab, getVisibleTabCount() - 1));
        if (clamped == selectedTab) return;
        selectedTab = clamped;
        build();
        sharedStashView.refreshTabSelector();
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