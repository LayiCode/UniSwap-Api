package com.olamide.UniSwap.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES =
            List.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadRoot;
    private final String baseUrl;

    public FileStorageService(
            @Value("${app.upload.dir}") String uploadDir,
            @Value("${app.upload.base-url}") String baseUrl
    ) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;

        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + uploadRoot, e);
        }
    }

    // Validates, saves the file under a random filename, and returns the
    // public URL to access it. Never trusts the client's original filename
    // for anything other than a first guess at the extension — the stored
    // extension is derived from the file's actual magic bytes instead.
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file was uploaded");
        }

        String declaredType = file.getContentType();
        if (declaredType == null || !ALLOWED_CONTENT_TYPES.contains(declaredType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, or WEBP images are allowed");
        }

        // The Content-Type header is client-supplied and trivially spoofable
        // (claim image/png while uploading an .html payload). Verify the
        // actual file signature before accepting it.
        ImageType detected = detectImageType(readHeader(file));
        if (detected == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File contents do not match a valid JPEG, PNG, or WEBP image");
        }

        String storedFilename = UUID.randomUUID() + detected.extension;

        // Resolve then re-check the path stays inside uploadRoot — defends
        // against a crafted filename trying to escape the upload directory
        // (path traversal), even though we generate the filename ourselves.
        Path target = uploadRoot.resolve(storedFilename).normalize();
        if (!target.getParent().equals(uploadRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }

        return baseUrl + "/" + storedFilename;
    }

    // Best-effort removal of a previously stored file (used when a listing is
    // deleted or its image replaced). Never fails the caller — a stray file
    // is far less damaging than a failed DB operation.
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        if (filename.isEmpty() || filename.contains("..")) {
            return;
        }

        Path target = uploadRoot.resolve(filename).normalize();
        if (!target.getParent().equals(uploadRoot)) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // Logged nowhere loud on purpose — cleanup is best-effort.
        }
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(16);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file");
        }
    }

    private ImageType detectImageType(byte[] header) {
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG; // JPEG: FF D8 FF
        }
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return ImageType.PNG; // PNG: 89 50 4E 47 0D 0A 1A 0A
        }
        if (header.length >= 12
                && asciiEquals(header, 0, "RIFF") && asciiEquals(header, 8, "WEBP")) {
            return ImageType.WEBP; // RIFF....WEBP
        }
        return null;
    }

    private boolean asciiEquals(byte[] header, int offset, String expected) {
        byte[] bytes = expected.getBytes();
        if (offset + bytes.length > header.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (header[offset + i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    private enum ImageType {
        JPEG(".jpg"),
        PNG(".png"),
        WEBP(".webp");

        final String extension;

        ImageType(String extension) {
            this.extension = extension;
        }
    }
}
