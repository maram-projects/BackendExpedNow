package com.example.ExpedNow.services.core.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private Path fileStorageLocation;

    public FileStorageService(@Value("${file.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
            logger.info("File storage directory created at: {}", this.fileStorageLocation);
        } catch (Exception ex) {
            logger.error("Could not create file storage directory", ex);
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    /**
     * Store file and return the relative file path
     */
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or null");
        }

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            // Check if the file's name contains invalid characters
            if (originalFileName.contains("..")) {
                throw new IllegalArgumentException("Sorry! Filename contains invalid path sequence " + originalFileName);
            }

            // Generate unique filename with timestamp
            String fileExtension = getFileExtension(originalFileName);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "img_" + timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + fileExtension;

            // Copy file to the target location
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            logger.info("File stored successfully: {} (original: {})", fileName, originalFileName);
            return fileName; // Return just the filename, not the full path

        } catch (IOException ex) {
            logger.error("Could not store file {}", originalFileName, ex);
            throw new RuntimeException("Could not store file " + originalFileName + ". Please try again!", ex);
        }
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "jpg"; // Default extension
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "jpg"; // Default extension
        }

        return filename.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * Get full file path
     */
    public Path getFilePath(String fileName) {
        return this.fileStorageLocation.resolve(fileName).normalize();
    }

    /**
     * Get file URL for web access
     */
    public String getFileUrl(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        return "/api/files/" + fileName; // Assuming you have a file serving endpoint
    }

    /**
     * Delete file
     */
    public boolean deleteFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }

        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            boolean deleted = Files.deleteIfExists(filePath);

            if (deleted) {
                logger.info("File deleted successfully: {}", fileName);
            } else {
                logger.warn("File not found for deletion: {}", fileName);
            }

            return deleted;
        } catch (IOException ex) {
            logger.error("Error deleting file: {}", fileName, ex);
            return false;
        }
    }

    /**
     * Check if file exists
     */
    public boolean fileExists(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }

        Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
        return Files.exists(filePath);
    }

    /**
     * Get file size
     */
    public long getFileSize(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return -1;
        }

        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            return Files.size(filePath);
        } catch (IOException ex) {
            logger.error("Error getting file size: {}", fileName, ex);
            return -1;
        }
    }

    /**
     * Validate image file
     */
    public boolean isValidImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return false;
        }

        return contentType.startsWith("image/") &&
                (contentType.equals("image/jpeg") ||
                        contentType.equals("image/jpg") ||
                        contentType.equals("image/png") ||
                        contentType.equals("image/gif") ||
                        contentType.equals("image/bmp") ||
                        contentType.equals("image/webp"));
    }

    /**
     * Get storage directory info
     */
    public String getStorageInfo() {
        try {
            long totalSpace = Files.getFileStore(fileStorageLocation).getTotalSpace();
            long usableSpace = Files.getFileStore(fileStorageLocation).getUsableSpace();

            return String.format("Storage Location: %s, Total Space: %d MB, Available Space: %d MB",
                    fileStorageLocation.toString(),
                    totalSpace / (1024 * 1024),
                    usableSpace / (1024 * 1024));
        } catch (IOException ex) {
            logger.error("Error getting storage info", ex);
            return "Storage info unavailable";
        }
    }
}