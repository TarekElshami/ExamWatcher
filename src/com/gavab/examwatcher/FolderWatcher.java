package com.gavab.examwatcher;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 *
 * @author tarek
 */

public class FolderWatcher {

    private final String folderPath;

    public FolderWatcher(String folderPath) {
        this.folderPath = folderPath;
    }

    // Devuelve un snapshot inicial de todos los archivos
    public List<String> getInitialSnapshot() {
        List<String> snapshot = new ArrayList<>();
        snapshot.add("=== Initial folder snapshot: " + folderPath + " ===");

        try {
            Files.walk(Paths.get(folderPath))
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
