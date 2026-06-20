package gomule.d2i;

import com.google.common.io.BaseEncoding;
import gomule.d2i.D2SharedStash.D2SharedStashPane;
import gomule.item.D2Item;
import gomule.util.D2BitReader;

import java.util.ArrayList;
import java.util.List;

public class D2SharedStashReader {

    static final byte[] STASH_HEADER_START = BaseEncoding.base16().decode("55AA55AA");

    public D2SharedStash readStash(String filename) throws Exception {
        return readStash(filename, new D2BitReader(filename));
    }

    public D2SharedStash readStash(String filename, D2BitReader bitReader) throws Exception {
        int[] stashHeaderOffsets = bitReader.findBytes(STASH_HEADER_START);
        if (stashHeaderOffsets.length > 7) throw new RuntimeException("Stash unsupported");
        List<D2SharedStashPane> result = new ArrayList<>();
        for (int paneIndex = 0; paneIndex < stashHeaderOffsets.length; paneIndex++) {
            bitReader.set_byte_pos(stashHeaderOffsets[paneIndex]);
            // Each pane's start offset above was found by scanning the raw bytes for the
            // "55AA55AA" marker up front, independent of how any earlier pane's contents get
            // interpreted -- so a pane that fails to parse (its own try/catch below) doesn't
            // prevent finding or reading whatever panes come after it; only that one pane's
            // remaining items are lost. A failure reading the header itself, before any items
            // are read, is treated the same way: keep an empty, marked-incomplete pane and move
            // on to the next, instead of losing every pane already read or aborting before
            // trying the rest.
            try {
                result.add(readSharedStashPane(bitReader, filename));
            } catch (Exception pEx) {
                result.add(D2SharedStashPane.fromItemsPartial(new ArrayList<>(), 0,
                        "Pane " + (paneIndex + 1) + " of " + stashHeaderOffsets.length + " failed to parse: " + pEx));
            }
        }
        return new D2SharedStash(filename, result, bitReader.getFileContent());
    }

    private D2SharedStashPane readSharedStashPane(D2BitReader bitReader, String filename) throws Exception {
        int stashPaneStart = bitReader.get_byte_pos();
        D2SharedStash.Header header = D2SharedStash.Header.fromBytes(bitReader);
        // Same version drift as the .d2s character format (see D2Character.readChar() and
        // D2Item.setFormatVersion()): the header layout itself hasn't changed, but a strict
        // equality check rejects every later D2R patch. Accept 99 and anything newer.
        if (header.getVersion() < 99)
            throw new RuntimeException("Incorrect shared stash version: " + header.getVersion());
        D2Item.setFormatVersion(header.getVersion());
        bitReader.set_byte_pos(bitReader.findNextFlag("JM", bitReader.get_byte_pos()));
        bitReader.skipBytes(2);
        int numItems = (int) bitReader.read(16);
        List<D2Item> result = new ArrayList<>();
        // Same situation as D2Character.readItems(): there's no reliable way to skip just one bad
        // item and resync on the next, since each item's length is only known once it has parsed
        // successfully. Keep everything read so far in this pane and stop -- found via a real
        // shared stash whose "rune/gem" tab turned out not to store its contents as regular items
        // at all (still being investigated), which otherwise made every item in every pane after
        // it, and the rest of the stash with them, fail to load too.
        for (int i = 0; i < numItems; i++) {
            try {
                result.add(new D2Item(filename, bitReader, 75));
            } catch (Exception pEx) {
                String reason = "Item " + (i + 1) + " of " + numItems + " failed to parse: " + pEx;
                try {
                    return D2SharedStashPane.fromItemsPartial(result, header.getGold(), reason);
                } catch (Exception pGridEx) {
                    // Building the grid for the items read so far failed too (e.g. one of them
                    // has an overlapping/corrupt position, itself a symptom of the same
                    // misalignment that made the next item fail) -- fall back to an empty pane
                    // rather than letting this escape and be mistaken, by the per-pane catch in
                    // readStash(), for a completely different pane's failure.
                    return D2SharedStashPane.fromItemsPartial(new ArrayList<>(), header.getGold(),
                            reason + "; also failed to keep the items read before that: " + pGridEx);
                }
            }
        }
        int calculatedLength = bitReader.get_byte_pos() - stashPaneStart;
        if (calculatedLength != header.getLength())
            throw new RuntimeException("Incorrect shared stash length: " + calculatedLength + " expected: " + header.getLength());
        return D2SharedStashPane.fromItems(result, header.getGold());
    }
}
