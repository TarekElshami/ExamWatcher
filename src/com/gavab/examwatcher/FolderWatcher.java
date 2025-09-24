package com.gavab.examwatcher;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 * @author tarek
 */

public class FolderWatcher {

    private final String projectFolder;
    private Map<String, FileInfo> lastSnapshot = new HashMap<>();
    
    // Clase interna para almacenar información de archivos
    private static class FileInfo {
        long size;
        long lines;
        long lastModified;
        
        FileInfo(long size, long lines, long lastModified) {
            this.size = size;
            this.lines = lines;
            this.lastModified = lastModified;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof FileInfo)) return false;
            FileInfo other = (FileInfo) obj;
            return size == other.size && lines == other.lines && lastModified == other.lastModified;
        }
    }

    public FolderWatcher(String folderPath) {
        this.projectFolder = folderPath;
        this.lastSnapshot = createCurrentSnapshot();
    }
    
    private Map<String, FileInfo> createCurrentSnapshot() {
        Map<String, FileInfo> snapshot = new HashMap<>();
        
        try {
            Files.walk(Paths.get(projectFolder))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        File file = path.toFile();
                        long size = file.length();
                        long lines = countLines(path);
                        long lastModified = file.lastModified();
                        
                        snapshot.put(path.toString(), new FileInfo(size, lines, lastModified));
                    } catch (IOException e) {
                        // Si no se puede leer, lo ignoramos
                        System.err.println("Could not read file: " + path.toString());
                    }
                });
        } catch (IOException e) {
            System.err.println("Error reading folder: " + e.getMessage());
        }
        
        return snapshot;
    }
    
    private long countLines(Path path) throws IOException {
        try {
            return Files.lines(path).count();
        } catch (Exception e) {
            // Para archivos binarios o con problemas de encoding
            return 0;
        }
    }
    
    /**
     * Verifica si hay cambios y retorna información detallada
     * @return Lista de mensajes con los cambios detectados, o null si no hay cambios
     */
    public List<String> checkChangesDetailed() {
        Map<String, FileInfo> currentSnapshot = createCurrentSnapshot();
        List<String> changes = new ArrayList<>();
        
        String timestamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
        
        // Verificar archivos modificados o nuevos
        for (Map.Entry<String, FileInfo> entry : currentSnapshot.entrySet()) {
            String filePath = entry.getKey();
            FileInfo currentInfo = entry.getValue();
            FileInfo previousInfo = lastSnapshot.get(filePath);
            
            if (previousInfo == null) {
                // Archivo nuevo
                changes.add(String.format("%s - NEW FILE: %s (%d chars, %d lines)", 
                    timestamp, filePath, currentInfo.size, currentInfo.lines));
            } else if (!currentInfo.equals(previousInfo)) {
                // Archivo modificado
                long charDiff = currentInfo.size - previousInfo.size;
                long linesDiff = currentInfo.lines - previousInfo.lines;
                
                changes.add(String.format("%s - MODIFIED: %s (chars: %+d [%d→%d], lines: %+d [%d→%d])", 
                    timestamp, filePath, 
                    charDiff, previousInfo.size, currentInfo.size,
                    linesDiff, previousInfo.lines, currentInfo.lines));
            }
        }
        
        // Verificar archivos eliminados
        for (String filePath : lastSnapshot.keySet()) {
            if (!currentSnapshot.containsKey(filePath)) {
                FileInfo deletedInfo = lastSnapshot.get(filePath);
                changes.add(String.format("%s - DELETED FILE: %s (was %d chars, %d lines)", 
                    timestamp, filePath, deletedInfo.size, deletedInfo.lines));
            }
        }
        
        // Actualizar snapshot
        lastSnapshot = currentSnapshot;
        
        return changes.isEmpty() ? null : changes;
    }
    
    /**
     * Método de compatibilidad con la versión anterior
     */
    public boolean checkChange() {
        List<String> changes = checkChangesDetailed();
        return changes != null && !changes.isEmpty();
    }

    /**
     * Devuelve un snapshot inicial de todos los archivos
     */
    public List<String> getInitialSnapshot() {
        List<String> snapshot = new ArrayList<>();
        snapshot.add("=== Initial folder snapshot: " + projectFolder + " ===");

        try {
            Files.walk(Paths.get(projectFolder))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        long size = Files.size(path);
                        long lines = countLines(path);
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