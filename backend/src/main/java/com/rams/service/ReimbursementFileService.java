package com.rams.service;

import com.rams.config.UploadProperties;
import com.rams.web.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReimbursementFileService {

    private static final long MAX_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );
    private static final Map<String, String> EXTENSIONS = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

    private final Path root;

    public ReimbursementFileService(UploadProperties properties) {
        this.root = Path.of(properties.getDirectory()).toAbsolutePath().normalize().resolve("reimbursements");
    }

    @PostConstruct
    void ensureDirectory() throws IOException {
        Files.createDirectories(root);
    }

    public StoredFile store(Long reimbursementId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a receipt file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Receipt files must be 10 MB or smaller");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(contentType)) {
            throw ApiException.badRequest("Use a PDF, JPEG, PNG, WEBP, or GIF receipt");
        }
        String original = sanitizeFilename(file.getOriginalFilename());
        String filename = UUID.randomUUID() + EXTENSIONS.get(contentType);
        Path folder = root.resolve(String.valueOf(reimbursementId)).normalize();
        if (!folder.startsWith(root)) {
            throw ApiException.badRequest("Invalid upload path");
        }
        Path destination = folder.resolve(filename).normalize();
        if (!destination.startsWith(folder)) {
            throw ApiException.badRequest("Invalid upload path");
        }
        try {
            Files.createDirectories(folder);
            file.transferTo(destination);
        } catch (IOException exception) {
            throw ApiException.badRequest("Could not save the receipt");
        }
        return new StoredFile(
                original,
                contentType,
                file.getSize(),
                "/api/uploads/reimbursements/" + reimbursementId + "/" + filename
        );
    }

    public Resource load(String storageUrl) {
        Path file = resolve(storageUrl);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw ApiException.notFound("Receipt file not found");
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw ApiException.notFound("Receipt file not found");
        }
    }

    public void deleteIfOwned(String storageUrl) {
        Path file = resolveQuietly(storageUrl);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            /* best effort */
        }
    }

    private Path resolve(String storageUrl) {
        Path file = resolveQuietly(storageUrl);
        if (file == null || !Files.exists(file)) {
            throw ApiException.notFound("Receipt file not found");
        }
        return file;
    }

    private Path resolveQuietly(String storageUrl) {
        if (storageUrl == null || !storageUrl.startsWith("/api/uploads/reimbursements/")) {
            return null;
        }
        String relative = storageUrl.substring("/api/uploads/reimbursements/".length());
        if (relative.contains("..")) {
            return null;
        }
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root)) {
            return null;
        }
        return file;
    }

    private static String sanitizeFilename(String originalFilename) {
        String value = originalFilename == null ? "receipt" : originalFilename.trim();
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[^A-Za-z0-9._\\- ]", "_");
        if (value.isBlank()) {
            value = "receipt";
        }
        if (value.length() > 200) {
            value = value.substring(value.length() - 200);
        }
        return value;
    }

    public record StoredFile(String originalFilename, String contentType, long fileSize, String storageUrl) {
    }
}
