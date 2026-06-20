package randall.flavie;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlavieTest {

    // Generating a Flavie report crashed with a NullPointerException every single time, for as
    // long as a "dupelists" directory (relative to GoMule's working directory) hadn't existed --
    // which it never has, in this repo or in a fresh checkout/build (see Flavie.java's comment).
    // File.listFiles() returns null, not an empty array, for a directory that doesn't exist.
    @Test
    public void missingDupeListsDirectoryProducesNoFiltersInsteadOfThrowing() throws Exception {
        File doesNotExist = new File("this-directory-does-not-exist-" + System.nanoTime());
        assertTrue(!doesNotExist.exists());

        ArrayList filters = Flavie.loadDupeFilters(doesNotExist);

        assertEquals(0, filters.size());
    }

    @Test
    public void emptyDupeListsDirectoryProducesNoFilters() throws Exception {
        File tempDir = File.createTempFile("dupelists", "").getAbsoluteFile();
        tempDir.delete();
        tempDir.mkdir();
        tempDir.deleteOnExit();

        ArrayList filters = Flavie.loadDupeFilters(tempDir);

        assertEquals(0, filters.size());
    }

    @Test
    public void txtFileInDupeListsDirectoryProducesOneFilter() throws Exception {
        File tempDir = File.createTempFile("dupelists", "").getAbsoluteFile();
        tempDir.delete();
        tempDir.mkdir();
        tempDir.deleteOnExit();
        File dupeFile = new File(tempDir, "known-dupes.txt");
        dupeFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(dupeFile)) {
            writer.write("Some Item Name 0xdeadbeef\n");
        }

        ArrayList filters = Flavie.loadDupeFilters(tempDir);

        assertEquals(1, filters.size());
    }

    @Test
    public void zipFileInDupeListsDirectoryProducesOneFilterPerTxtEntry() throws Exception {
        File tempDir = File.createTempFile("dupelists", "").getAbsoluteFile();
        tempDir.delete();
        tempDir.mkdir();
        tempDir.deleteOnExit();
        File zipFile = new File(tempDir, "known-dupes.zip");
        zipFile.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new java.io.FileOutputStream(zipFile))) {
            zip.putNextEntry(new ZipEntry("first.txt"));
            zip.write("Some Item Name 0xdeadbeef\n".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("second.txt"));
            zip.write("Another Item Name 0xfeedface\n".getBytes());
            zip.closeEntry();
            // Non-.txt entries inside the zip are skipped, same as non-.txt/.zip files directly
            // in the directory.
            zip.putNextEntry(new ZipEntry("readme.md"));
            zip.write("not a dupe list".getBytes());
            zip.closeEntry();
        }

        ArrayList filters = Flavie.loadDupeFilters(tempDir);

        assertEquals(2, filters.size());
    }
}
