package com.rams.service;

import com.rams.config.UploadProperties;
import com.rams.web.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProfileImageService {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_BYTES = 2 * 1024 * 1024;

    private final Path root;

    public ProfileImageService(UploadProperties properties) {
        this.root = Path.of(properties.getDirectory()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureDirectory() throws IOException {
        Files.createDirectories(root.resolve("profiles"));
    }

    public String store(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a profile picture");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Profile picture must be 2 MB or smaller");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(contentType)) {
            throw ApiException.badRequest("Use a JPEG, PNG, WEBP, or GIF image");
        }
        String extension = extensionFor(contentType);
        String filename = userId + "-" + UUID.randomUUID() + extension;
        Path destination = root.resolve("profiles").resolve(filename).normalize();
        if (!destination.startsWith(root)) {
            throw ApiException.badRequest("Invalid upload path");
        }
        try {
            file.transferTo(destination);
        } catch (IOException exception) {
            throw ApiException.badRequest("Could not save the profile picture");
        }
        return "/api/uploads/profiles/" + filename;
    }

    public void deleteIfOwned(String profileImageUrl) {
        if (profileImageUrl == null || !profileImageUrl.startsWith("/api/uploads/profiles/")) {
            return;
        }
        String filename = profileImageUrl.substring("/api/uploads/profiles/".length());
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return;
        }
        Path file = root.resolve("profiles").resolve(filename).normalize();
        if (!file.startsWith(root.resolve("profiles"))) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            /* best effort */
        }
    }

    public Path root() {
        return root;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }
}
