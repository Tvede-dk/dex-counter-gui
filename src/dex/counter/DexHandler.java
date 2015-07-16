/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dex.counter;

import com.android.dexdeps.DexData;
import info.persistent.dex.DexMethodCounts;
import java.awt.Frame;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 *
 * @author kasper
 */
public class DexHandler {

    private final Frame parrent;

    public DexHandler(Frame parrent) {
        this.parrent = parrent;
    }
    
    
    
    
    public void run(String fileName, boolean includeClasses,String packageFilter, int maxDepth, DexMethodCounts.Filter filter) throws IOException {
        List<RandomAccessFile> dexFiles = openInputFiles(fileName);
        DexMethodCounts.Node packageTree = new DexMethodCounts.Node();

        for (RandomAccessFile dexFile : dexFiles) {
            DexData dexData = new DexData(dexFile);
            dexData.load();
            DexMethodCounts.generate(
                    packageTree, dexData, includeClasses, packageFilter, maxDepth, filter);
            dexFile.close();
        }
        resultForm resultForm = new resultForm(parrent,false);
        resultForm.setNode(packageTree);
        resultForm.setVisible(true);
//        packageTree.output("");
    }

    /**
     * Opens an input file, which could be a .dex or a .jar/.apk with a
     * classes.dex inside. If the latter, we extract the contents to a temporary
     * file.
     */
    private static List<RandomAccessFile> openInputFiles(String fileName) throws IOException {
        List<RandomAccessFile> dexFiles = new ArrayList<>();

        openInputFileAsZip(fileName, dexFiles);
        if (dexFiles.isEmpty()) {
            File inputFile = new File(fileName);
            RandomAccessFile dexFile = new RandomAccessFile(inputFile, "r");
            dexFiles.add(dexFile);
        }

        return dexFiles;
    }

    /**
     * Tries to open an input file as a Zip archive (jar/apk) with a
     * "classes.dex" inside.
     */
    private static void openInputFileAsZip(String fileName, List<RandomAccessFile> dexFiles) throws IOException {
        ZipFile zipFile;

        // Try it as a zip file.
        try {
            zipFile = new ZipFile(fileName);
        } catch (FileNotFoundException fnfe) {
            // not found, no point in retrying as non-zip.
            System.err.println("Unable to open '" + fileName + "': "
                    + fnfe.getMessage());
            throw fnfe;
        } catch (ZipException ze) {
            // not a zip
            return;
        }

        // Open and add all files matching "classes.*\.dex" in the zip file.
        for (ZipEntry entry : Collections.list(zipFile.entries())) {
            if (entry.getName().matches("classes.*\\.dex")) {
                dexFiles.add(openDexFile(zipFile, entry));
            }
        }

        zipFile.close();
    }

    private static RandomAccessFile openDexFile(ZipFile zipFile, ZipEntry entry) throws IOException {
        // We know it's a zip; see if there's anything useful inside.  A
        // failure here results in some type of IOException (of which
        // ZipException is a subclass).
        InputStream zis = zipFile.getInputStream(entry);

        // Create a temp file to hold the DEX data, open it, and delete it
        // to ensure it doesn't hang around if we fail.
        File tempFile = File.createTempFile("dexdeps", ".dex");
        RandomAccessFile dexFile = new RandomAccessFile(tempFile, "rw");
        tempFile.delete();

        // Copy all data from input stream to output file.
        byte copyBuf[] = new byte[32768];
        int actual;

        while (true) {
            actual = zis.read(copyBuf);
            if (actual == -1) {
                break;
            }

            dexFile.write(copyBuf, 0, actual);
        }

        dexFile.seek(0);

        return dexFile;
    }
}
