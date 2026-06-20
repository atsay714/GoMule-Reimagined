package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash;
import gomule.gui.D2ViewClipboard;
import gomule.gui.ItemRightClickMenu;
import gomule.item.D2Item;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class SharedStashPanelMouseClickHandler extends MouseAdapter {

    private final SharedStashPanel sharedStashPanel;

    public SharedStashPanelMouseClickHandler(SharedStashPanel sharedStashPanel) {
        this.sharedStashPanel = sharedStashPanel;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) handleLeftClick(e);
        if (e.getButton() == MouseEvent.BUTTON3) handleRightClick(e);
    }

    private void handleRightClick(MouseEvent e) {
        D2SharedStash sharedStash = sharedStashPanel.getSharedStash();
        if (sharedStash == null) return;
        int col = SharedStashPanel.getColForXCoord(e.getX());
        int row = SharedStashPanel.getRowForYCoord(e.getY());
        if (col < 0 || row < 0 || col > 15 || row > 12) return;
        D2SharedStash.D2SharedStashPane stashPane = sharedStashPanel.getSelectedStashPane();
        D2Item item = stashPane.getItemCovering(col, row);
        if (item != null) {
            new ItemRightClickMenu(item, this::deleteMenuItemAction).show(sharedStashPanel, e.getX(), e.getY() + 35);
        }
    }

    private void deleteMenuItemAction(D2Item d2Item) {
        removeItem(sharedStashPanel.getSelectedStashPane(), d2Item);
    }

    private void handleLeftClick(MouseEvent e) {
        D2SharedStash sharedStash = sharedStashPanel.getSharedStash();
        if (sharedStash == null) return;
        Integer possibleStashTabClick = getPossibleStashTabClick(e.getX(), e.getY());
        setStashTab(possibleStashTabClick);
        if (isClickOnGoldButton(e.getX(), e.getY())) showGoldDialog();

        int col = SharedStashPanel.getColForXCoord(e.getX());
        int row = SharedStashPanel.getRowForYCoord(e.getY());
        if (col < 0 || row < 0 || col > 15 || row > 12) return;
        D2SharedStash.D2SharedStashPane stashPane = sharedStashPanel.getSelectedStashPane();
        D2Item item = stashPane.getItemCovering(col, row);
        if (item != null) {
            moveItemToClipboard(stashPane, item);
        } else if (D2ViewClipboard.getItem() != null) {
            tryMoveItemFromClipboard(stashPane, col, row);
        }
    }

    private boolean isClickOnGoldButton(int x, int y) {
        return x >= 222 && x <= 243 && y >= 500 && y <= 522;
    }

    private void showGoldDialog() {
        JOptionPane.showConfirmDialog(sharedStashPanel, new SharedStashGoldTransferPanel(sharedStashPanel), "Transfer Gold",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE);
    }

    private void tryMoveItemFromClipboard(D2SharedStash.D2SharedStashPane stashPane, int col, int row) {
        D2SharedStash sharedStash = sharedStashPanel.getSharedStash();
        D2Item item = D2ViewClipboard.getItem();
        if (stashPane.canDropItem(col, row, item)) {
            D2SharedStash.D2SharedStashPane d2SharedStashPane = stashPane.addItem(col, row, D2ViewClipboard.removeItem());
            sharedStash.replacePane(sharedStashPanel.getSelectedStashPaneIndex(), d2SharedStashPane);
            sharedStash.setModified(true);
            sharedStashPanel.setCursorPickupItem();
        }
    }

    private void moveItemToClipboard(D2SharedStash.D2SharedStashPane stashPane, D2Item item) {
        removeItem(stashPane, item);
        D2ViewClipboard.addItem(item);
    }

    private void removeItem(D2SharedStash.D2SharedStashPane stashPane, D2Item item) {
        D2SharedStash sharedStash = sharedStashPanel.getSharedStash();
        D2SharedStash.D2SharedStashPane d2SharedStashPane = stashPane.removeItem(item);
        sharedStash.replacePane(sharedStashPanel.getSelectedStashPaneIndex(), d2SharedStashPane);
        sharedStash.setModified(true);
        sharedStashPanel.setCursorDropItem();
    }

    private void setStashTab(Integer possibleStashTabClick) {
        if (possibleStashTabClick == null) return;
        if (sharedStashPanel.getSelectedStashPaneIndex() == possibleStashTabClick) return;
        sharedStashPanel.setSelectedStashPaneIndex(possibleStashTabClick);
        sharedStashPanel.build();
    }

    // DLC-only tabs GoMule can't parse are painted over in SharedStashPanel.build() and must not
    // be clickable either -- not just because there's nothing there to click, but because clicking
    // in would show what looks like an empty page for items that are actually still safely
    // preserved (see SharedStashPanel.hideIncompleteTabs()'s comment). Non-DLC stashes never have
    // an incomplete pane, so isIncomplete() never excludes any of their (real, clickable) tabs.
    // See SharedStashPanel.getClickedTabIndex() for the actual decision logic.
    private Integer getPossibleStashTabClick(int x, int y) {
        return SharedStashPanel.getClickedTabIndex(x, y, this.sharedStashPanel.getSharedStash().getPanes());
    }
}
