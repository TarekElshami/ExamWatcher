package com.gavab.examwatcher;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Monitors file creation and deletion in the project folder
 * Creates hashes and backups of new files, stores notifications in memory
 * 
 * @author tarek
 */
public class CopyWatcher {
    
    private final String projectFolder;
    private final String backupFolder;
    private final ExecutorService executor;
    private volatile boolean isFinalizingExam = false;
    private Set<String> knownFiles = new HashSet<>();
    private final Object lockObject = new Object();
    private List<String> copyWatcherMessages = new ArrayList<>();
    
    public CopyWatcher(String projectFolder) {
        this.projectFolder = projectFolder;
        this.backupFolder = projectFolder + File.separator + ".copywatcher_backup";
        this.executor = Executors.newSingleThreadExecutor();
        
        // Create backup folder if it doesn't exist
        createBackupFolder();
        
        // Initialize known files
        initializeKnownFiles();
    }
    
    private void createBackupFolder() {
        try {
            Files.createDirectories(Paths.get(backupFolder));
        } catch (IOException e) {
            System.err.println("Error creating backup folder: " + e.getMessage());
        }
    }
    
    private void initializeKnownFiles() {
        try {
            Files.walk(Paths.get(projectFolder))
                .filter(Files::isRegularFile)
                .filter(path -> !path.toString().startsWith(backupFolder)) // Ignore backup folder
                .filter(path -> !path.getFileName().toString().equals("resume.txt")) // Ignore resume.txt
                .forEach(path -> knownFiles.add(path.toString()));
        } catch (IOException e) {
            System.err.println("Error initializing known files: " + e.getMessage());
        }
    }
    
    /**
     * Called by FinishExam to signal that the exam is being finalized
     */
    public void setFinalizingExam(boolean finalizing) {
        synchronized (lockObject) {
            this.isFinalizingExam = finalizing;
        }
    }
    
    /**
     * Check for new or deleted files and process them
     */
    public void checkForChanges() {
        if (isFinalizingExam) {
            return; // Don't process during exam finalization
        }

        Set<String> currentFiles = new HashSet<>();

        try {
            // Get current files and directories (excluding backup folder and resume.txt)
            Files.walk(Paths.get(projectFolder))
                .filter(path -> !path.toString().startsWith(backupFolder))
                .filter(path -> !path.getFileName().toString().equals("resume.txt"))
                .forEach(path -> {
                    currentFiles.add(path.toString());
                    System.out.println("CopyWatcher DEBUG: Found path: " + path.toString());
                });

            System.out.println("CopyWatcher DEBUG: Current entries count: " + currentFiles.size()
                + ", Known entries count: " + knownFiles.size());

            // Check for new files/directories
            Set<String> newFiles = new HashSet<>(currentFiles);
            newFiles.removeAll(knownFiles);

            // Check for deleted files/directories
            Set<String> deletedFiles = new HashSet<>(knownFiles);
            deletedFiles.removeAll(currentFiles);

            if (!newFiles.isEmpty()) {
                System.out.println("CopyWatcher DEBUG: New entries detected: " + newFiles);
            }
            if (!deletedFiles.isEmpty()) {
                System.out.println("CopyWatcher DEBUG: Deleted entries detected: " + deletedFiles);
            }

            // Process new files/directories
            for (String newFile : newFiles) {
                Path path = Paths.get(newFile);
                if (Files.isDirectory(path)) {
                    notifyDirectoryCreation(newFile);
                } else {
                    processNewFile(newFile);
                }
            }

            // Process deleted files/directories
            for (String deletedFile : deletedFiles) {
                Path path = Paths.get(deletedFile);
                if (Files.isDirectory(path)) {
                    notifyDirectoryDeletion(deletedFile);
                } else {
                    processDeletedFile(deletedFile);
                }
            }

            // Update known files
            knownFiles = new HashSet<>(currentFiles);

        } catch (IOException e) {
            System.err.println("Error checking for file changes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void notifyDirectoryCreation(String dirPath) {
        synchronized (lockObject) {
            String timestamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
            String message = String.format("%s - DIRECTORY COPIED: %s", timestamp, dirPath);

            copyWatcherMessages.add(message);
            System.out.println("CopyWatcher: " + message);
        }
    }

    private void notifyDirectoryDeletion(String dirPath) {
        synchronized (lockObject) {
            String timestamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
            String message = String.format("%s - DIRECTORY DELETED: %s", timestamp, dirPath);

            copyWatcherMessages.add(message);
            System.out.println("CopyWatcher: " + message);
        }
    }
    
    
    private void processNewFile(String filePath) {
        try {
            System.out.println("CopyWatcher DEBUG: Starting to process new file: " + filePath);
            
            // Wait for file to be completely written
            waitForFileCompletion(filePath);
            System.out.println("CopyWatcher DEBUG: File completion confirmed for: " + filePath);
            
            // Create hash of content
            int hash = calculateFileHash(filePath);
            System.out.println("CopyWatcher DEBUG: Hash calculated for " + filePath + ": " + hash);
            
            // Create backup copy
            String backupPath = createBackupCopy(filePath, hash);
            System.out.println("CopyWatcher DEBUG: Backup created at: " + backupPath);
            
            // Store notification in memory
            notifyFileCreation(filePath, hash, backupPath);
            System.out.println("CopyWatcher DEBUG: Notification stored for: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing new file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void processDeletedFile(String filePath) {
        try {
            System.out.println("CopyWatcher DEBUG: Processing deleted file: " + filePath);
            
            // Store notification in memory
            notifyFileDeletion(filePath);
            System.out.println("CopyWatcher DEBUG: Deletion notification stored for: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing deleted file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void waitForFileCompletion(String filePath) throws InterruptedException {
        Path path = Paths.get(filePath);
        long previousSize = -1;
        long currentSize = 0;
        int stableCount = 0;
        
        // Wait until file size is stable for at least 3 checks (1.5 seconds)
        while (stableCount < 3) {
            try {
                currentSize = Files.size(path);
                if (currentSize == previousSize) {
                    stableCount++;
                } else {
                    stableCount = 0;
                    previousSize = currentSize;
                }
                Thread.sleep(500); // Wait 500ms between checks
            } catch (IOException e) {
                // File might not exist yet or be locked, wait and try again
                Thread.sleep(500);
                stableCount = 0;
            }
        }
    }
    
    private int calculateFileHash(String filePath) {
        int hashValue = 29366927; // Same initial value as FinishExam
        
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    final int mult = (i % 2) == 0 ? 1 : 100;
                    hashValue = hashValue + buffer[i] * mult;
                }
            }
        } catch (IOException e) {
            System.err.println("Error calculating hash for " + filePath + ": " + e.getMessage());
            // For empty files or read errors, return the base hash
        }
        
        return hashValue;
    }
    
    private String createBackupCopy(String originalPath, int hash) throws IOException {
        Path original = Paths.get(originalPath);
        String fileName = original.getFileName().toString();
        String backupFileName = hash + "_" + fileName;
        Path backupPath = Paths.get(backupFolder, backupFileName);
        
        // Copy file to backup location
        Files.copy(original, backupPath, StandardCopyOption.REPLACE_EXISTING);
        
        return backupPath.toString();
    }
    
    private void notifyFileCreation(String filePath, int hash, String backupPath) {
        synchronized (lockObject) {
            String timestamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
            String message = String.format("%s - FILE COPIED: %s | Hash: %d | Backup: %s", 
                timestamp, filePath, hash, backupPath);
            
            copyWatcherMessages.add(message);
            System.out.println("CopyWatcher: " + message);
        }
    }
    
    private void notifyFileDeletion(String filePath) {
        synchronized (lockObject) {
            String timestamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
            String message = String.format("%s - FILE DELETED: %s", timestamp, filePath);
            
            copyWatcherMessages.add(message);
            System.out.println("CopyWatcher: " + message);
        }
    }
    
    /**
     * Get all CopyWatcher messages to include in final resume
     */
    public List<String> getCopyWatcherMessages() {
        synchronized (lockObject) {
            return new ArrayList<>(copyWatcherMessages);
        }
    }
    
    /**
     * Clean up resources
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
    
    /**
     * Get the backup folder path
     */
    public String getBackupFolder() {
        return backupFolder;
    }
}