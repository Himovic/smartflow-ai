package com.dnd.smartflow.files;

import com.dnd.smartflow.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

/**
 * Stores evidence files on a local/mounted volume, partitioned by tenant. Storage keys are generated
 * server-side, so user-supplied file names can never be used for path traversal. The interface is small
 * enough to be swapped for S3/MinIO in production.
 */
@Service
public class StorageService {
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf",
            "text/plain", "text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword", "application/vnd.ms-excel");
    private final Path root;

    public StorageService(@Value("${smartflow.storage.path:./data/files}") String path) throws IOException {
        this.root = Paths.get(path).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String store(UUID tenantId, String contentType, InputStream in) {
        if (contentType == null || !ALLOWED.contains(contentType)) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", "Allowed: images, PDF, text, CSV and Office documents");
        }
        String key = tenantId + "/" + UUID.randomUUID();
        Path target = root.resolve(key).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot store file", e);
        }
        return key;
    }

    public byte[] read(UUID tenantId, String key) {
        if (!key.startsWith(tenantId + "/")) throw ApiException.notFound("File");
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw ApiException.notFound("File");
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw ApiException.notFound("File");
        }
    }

    public void delete(String key) {
        try {
            Path p = root.resolve(key).normalize();
            if (p.startsWith(root)) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
