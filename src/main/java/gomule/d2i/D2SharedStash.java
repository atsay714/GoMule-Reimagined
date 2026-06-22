package gomule.d2i;

import gomule.gui.D2ItemListAdapter;
import gomule.item.D2Item;
import gomule.util.D2Backup;
import gomule.util.D2BitReader;
import gomule.util.D2Project;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class D2SharedStash extends D2ItemListAdapter {
    private final List<D2SharedStashPane> panes;
    private final byte[] originalContent;
    private final D2SharedStashWriter sharedStashWriter;

    public D2SharedStash(String pFileName, List<D2SharedStashPane> panes, byte[] originalContent) {
        super(pFileName);
        this.panes = panes;
        this.originalContent = originalContent;
        this.sharedStashWriter = new D2SharedStashWriter(pFileName, originalContent);
    }

    public D2SharedStashPane getPane(int index) {
        return panes.get(index);
    }

    public List<D2SharedStashPane> getPanes() {
        return panes;
    }

    // How many tabs the UI should show. The Reimagined DLC converted its trailing "stackable
    // stash" tabs to a storage format GoMule can't decode, so they load as incomplete panes (see
    // D2SharedStashReader). Those are hidden from the tab strip -- but they stay in `panes` and are
    // written back byte-for-byte on save (see saveInternal()), so this only affects display, never
    // the file. Only a *trailing* run of incomplete panes is hidden: the tab strip is positional,
    // so hiding a middle tab would leave a gap, and in every real file these converted tabs are the
    // last ones. At least one tab always stays visible, even in the degenerate all-incomplete case.
    public int getVisibleTabCount() {
        int count = panes.size();
        while (count > 1 && panes.get(count - 1).isIncomplete()) {
            count--;
        }
        return count;
    }

    // True only when a *visible* tab failed to load -- a genuine partial-load problem worth warning
    // the user about. The DLC's converted "stackable stash" tabs are incomplete by nature but
    // hidden (see getVisibleTabCount()), so they must NOT trigger the warning. isItemsIncomplete()
    // remains the raw-data truth (any pane incomplete) for callers like the reader tests and save
    // path that need it.
    public boolean hasVisibleIncompletePane() {
        int visible = getVisibleTabCount();
        for (int i = 0; i < visible; i++) {
            if (panes.get(i).isIncomplete()) return true;
        }
        return false;
    }

    // The reasons behind hasVisibleIncompletePane(), for the tooltip -- only the visible failures,
    // excluding the expected/hidden converted tabs that getItemsIncompleteReason() would include.
    public String getVisibleIncompleteReason() {
        return panes.subList(0, getVisibleTabCount()).stream()
                .filter(D2SharedStashPane::isIncomplete)
                .map(D2SharedStashPane::getIncompleteReason)
                .collect(Collectors.joining("; "));
    }

    // Mirrors D2Character's isItemsIncomplete()/getItemsIncompleteReason(): a real shared stash
    // can have a tab (or part of one) that fails to parse -- e.g. a "rune/gem" tab whose contents
    // turned out not to be stored as regular items at all, just one real example so far -- and
    // there's no reliable way to resync mid-pane once that happens (see D2Character's own comment
    // for why). Every other pane, and everything read in the failing pane before the failure,
    // is kept and shown instead of losing the whole stash. Unlike D2Character, this doesn't block
    // saving outright: each incomplete pane's exact original bytes are preserved (see
    // D2SharedStashPane.getOriginalBytes()) and written back byte-for-byte regardless of its
    // (empty, or partially-read) in-memory item list, so editing and saving the rest of the
    // stash is still safe -- saveInternal() only refuses if an incomplete pane is somehow missing
    // its preserved bytes, which a normal read never produces.
    public boolean isItemsIncomplete() {
        return panes.stream().anyMatch(D2SharedStashPane::isIncomplete);
    }

    public String getItemsIncompleteReason() {
        return panes.stream()
                .filter(D2SharedStashPane::isIncomplete)
                .map(D2SharedStashPane::getIncompleteReason)
                .collect(Collectors.joining("; "));
    }

    @Override
    public boolean containsItem(D2Item pItem) {
        return panes.stream().anyMatch(it -> it.items.contains(pItem));
    }

    @Override
    public void removeItem(D2Item pItem) {
        //Handled by panes
    }

    @Override
    public void addItem(D2Item pItem) {
        //Handled by panes
    }

    @Override
    public List<D2Item> getItemList() {
        return panes.stream().flatMap(it -> it.getItems().stream()).collect(Collectors.toList());
    }

    @Override
    public int getNrItems() {
        return panes.stream().map(it -> it.items.size()).reduce(0, Integer::sum);
    }

    @Override
    public String getFilename() {
        return iFileName;
    }

    @Override
    public boolean isSC() {
        return iFileName.toLowerCase(Locale.forLanguageTag("UTF-8")).contains("softcore");
    }

    @Override
    public boolean isHC() {
        return !isSC();
    }

    @Override
    public void fullDump(PrintWriter pWriter) {
        pWriter.println(iFileName);
        pWriter.println();
        List<D2Item> items = getItemList();
        for (D2Item item : items) {
            item.toWriter(pWriter);
        }
        pWriter.println("Finished: " + iFileName);
        pWriter.println();
    }

    @Override
    protected void saveInternal(D2Project d2Project) {
        // Every incomplete pane must carry its preserved original bytes -- D2SharedStashReader
        // always sets them when it marks a pane incomplete, so this only fires if some other,
        // not-yet-existing code path produces an incomplete pane without them. Refuse rather than
        // silently write back an empty/truncated pane in that case.
        Optional<D2SharedStashPane> unsafePane = panes.stream()
                .filter(D2SharedStashPane::isIncomplete)
                .filter(p -> p.getOriginalBytes() == null)
                .findFirst();
        if (unsafePane.isPresent()) {
            throw new RuntimeException("Cannot save " + iFileName + ": its item list did not fully load ("
                    + unsafePane.get().getIncompleteReason() + ") and its original bytes weren't preserved");
        }
        if (d2Project != null) D2Backup.backup(d2Project, iFileName, new D2BitReader(originalContent.clone()));
        sharedStashWriter.write(this);
        setModified(false);
    }

    public void replacePane(int paneIndex, D2SharedStashPane newPane) {
        panes.set(paneIndex, newPane);
    }

    public static class D2SharedStashPane {
        private final List<D2Item> items;
        private final D2Item[][] paneGrid;
        private final int gold;
        private final String incompleteReason;
        private final byte[] originalBytes;

        D2SharedStashPane(List<D2Item> items, D2Item[][] paneGrid, int gold, String incompleteReason, byte[] originalBytes) {
            this.items = items;
            this.paneGrid = paneGrid;
            this.gold = gold;
            this.incompleteReason = incompleteReason;
            this.originalBytes = originalBytes;
        }

        public static D2SharedStashPane fromItems(List<D2Item> items, int gold) {
            return new D2SharedStashPane(items, constructPaneGrid(items), gold, null, null);
        }

        // Used when this pane's item list didn't fully parse -- see D2SharedStashReader's comment
        // for why a failure partway through can't be resynced, only stopped at. Everything read
        // before the failure is kept and shown, same as D2Character's per-item partial load, but
        // originalBytes (this pane's exact bytes in the real file, found independently of how its
        // contents got interpreted -- see D2SharedStashReader) is what actually gets written back
        // on save, regardless of what's in items: there's no way to write back a correct
        // reconstruction of a pane GoMule doesn't fully understand, so the safe thing is to leave
        // it untouched rather than risk corrupting or truncating it.
        public static D2SharedStashPane fromItemsPartial(List<D2Item> items, int gold, String incompleteReason, byte[] originalBytes) {
            return new D2SharedStashPane(items, constructPaneGrid(items), gold, incompleteReason, originalBytes);
        }

        public boolean isIncomplete() {
            return incompleteReason != null;
        }

        public String getIncompleteReason() {
            return incompleteReason;
        }

        // Non-null exactly when isIncomplete() -- see fromItemsPartial()'s comment. Used by
        // D2SharedStashWriter to write this pane back verbatim instead of reconstructing it.
        public byte[] getOriginalBytes() {
            return originalBytes;
        }

        private static D2Item[][] constructPaneGrid(List<D2Item> items) {
            D2Item[][] grid = new D2Item[16][13];
            for (D2Item item : items) {
                for (int i = item.get_col(); i < (int) item.get_col() + (int) item.get_width(); i++) {
                    for (int j = item.get_row(); j < (int) item.get_row() + (int) item.get_height(); j++) {
                        if (grid[i][j] != null) throw new RuntimeException("Failed to create shared stash pane");
                        grid[i][j] = item;
                    }
                }
            }
            return grid;
        }

        public List<D2Item> getItems() {
            return items;
        }

        public int getGold() {
            return gold;
        }

        public D2Item getItemCovering(int col, int row) {
            return paneGrid[col][row];
        }

        public boolean canDropItem(int col, int row, D2Item item) {
            if (item.isQuestItem()) return false;
            if (col > paneGrid.length - 1 || col < 0 || row > paneGrid[0].length - 1 || row < 0) return false;
            for (int i = col; i < col + item.get_width(); i++) {
                for (int j = row; j < row + item.get_height(); j++) {
                    if (i > paneGrid.length - 1 || j > paneGrid[0].length - 1) return false;
                    if (paneGrid[i][j] != null) return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "D2SharedStashPane{" +
                    "items=" + items +
                    ", paneGrid=" + Arrays.toString(paneGrid) +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            D2SharedStashPane that = (D2SharedStashPane) o;
            return Objects.equals(items, that.items) && Arrays.deepEquals(paneGrid, that.paneGrid);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(items);
            result = 31 * result + Arrays.deepHashCode(paneGrid);
            return result;
        }

        public D2SharedStashPane addItem(int col, int row, D2Item item) {
            item.set_col((short) col);
            item.set_row((short) row);
            item.set_location((short) 0);
            item.set_body_position((short) 0);
            item.set_panel((short) 5);
            item.setCharLvl(75);
            List<D2Item> items = new ArrayList<>(this.items);
            items.add(item);
            // Preserves incompleteReason and originalBytes (rather than going through
            // fromItems(), which would reset both to "complete") -- editing a pane that didn't
            // fully load doesn't make the part that failed to load any more loaded, and
            // originalBytes is what actually gets saved for an incomplete pane regardless of
            // this edit (see fromItemsPartial()'s comment) -- there's no real, supported way to
            // add an item to one of these in the current UI, but if it ever happens, saving
            // silently keeping the pane unchanged is the safe outcome, not corrupting it.
            return new D2SharedStashPane(items, constructPaneGrid(items), gold, incompleteReason, originalBytes);
        }

        public D2SharedStashPane removeItem(D2Item item) {
            List<D2Item> items = new ArrayList<>(this.items);
            items.remove(item);
            return new D2SharedStashPane(items, constructPaneGrid(items), gold, incompleteReason, originalBytes);
        }
    }

    static class Header {
        private final long version;
        private final int gold;
        private final long length;

        public Header(long version, int gold, long length) {
            this.version = version;
            this.gold = gold;
            this.length = length;
        }

        public static Header fromBytes(D2BitReader bitReader) {
            bitReader.skipBytes(8);
            long version = bitReader.read(8);
            bitReader.skipBytes(3);
            int gold = (int) bitReader.read(24);
            bitReader.skipBytes(1);
            long length = bitReader.read(32);
            return new D2SharedStash.Header(version, gold, length);
        }

        public long getVersion() {
            return version;
        }

        public int getGold() {
            return gold;
        }

        public long getLength() {
            return length;
        }

        @Override
        public String toString() {
            return "Header{" +
                    "version=" + version +
                    ", gold=" + gold +
                    ", length=" + length +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Header header = (Header) o;
            return version == header.version && gold == header.gold && length == header.length;
        }

        @Override
        public int hashCode() {
            return Objects.hash(version, gold, length);
        }
    }
}
