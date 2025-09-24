package com.gavab.examwatcher;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 *
 * @author tarek
 */

public class FolderWatcher {

    private final String projectFolder;
    private long lastTotalSize = 0;

    public FolderWatcher(String folderPath) {
        this.projectFolder = folderPath;
        this.lastTotalSize = calculateTotalSize();
    }
    
    private long calculateTotalSize() {
        return folderSize(new File(projectFolder));
    }

    private long folderSize(File folder) {
        long length = 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    length += file.length();
                } else {
                    length += folderSize(file);
                }
            }
        }
        return length;
    }
    
    public boolean checkChange() {
        long currentSize = calculateTotalSize();
        boolean changed = currentSize != lastTotalSize;
        lastTotalSize = currentSize;
        return changed;
    }

    // Devuelve un snapshot inicial de todos los archivos
    public List<String> getInitialSnapshot() {
        List<String> snapshot = new ArrayList<>();
        snapshot.add("=== Initial folder snapshot: " + projectFolder + " ===");

        try {
            Files.walk(Paths.get(projectFolder))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        long size = Files.size(path);
                        long lines = Files.lines(path).count();
                        snapshot.add(String.format(
                            "File: %s | Size: %d bytes | Lines: %d",
                            path.toString(), size, lines
                        ));
                    } catch (IOException e) {
                        snapshot.add("Could not read file: " + path.toString());
                    }
                });
        } catch (IOException e) {
            snapshot.add("Error reading folder: " + e.getMessage());
        }

        snapshot.add("=== End of snapshot ===");
        return snapshot;
    }
}
