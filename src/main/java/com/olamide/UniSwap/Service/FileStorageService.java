package com.olamide.UniSwap.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final Path uploadRoot;
    private final String baseUrl;
    private final String supabaseUrl;
    private final String supabaseServiceKey;
    private final String supabaseBucket;

    @Autowired
    public FileStorageService(
            @Value("${app.upload.dir}") String uploadDir,
            @Value("${app.upload.base-url}") String baseUrl,
            @Value("${app.upload.supabase-url:}") String supabaseUrl,
            @Value("${app.upload.supabase-service-key:}") String supabaseServiceKey,
            @Value("${app.upload.supabase-bucket:}") String supabaseBucket
    ) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
        this.supabaseUrl = normalizeSupabaseUrl(supabaseUrl);
        this.supabaseServiceKey = supabaseServiceKey;
        this.supabaseBucket = supabaseBucket;

        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + uploadRoot, e);
        }
    }

    // Disk-only convenience for the unit tests — no Supabase configured.
    FileStorageService(String uploadDir, String baseUrl) {
        this(uploadDir, baseUrl, "", "", "");
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

        if (supabaseEnabled()) {
            return supabaseStore(storedFilename, declaredType, file);
        }

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

        if (supabaseEnabled()) {
            deleteFromSupabase(filename);
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

    // Pushes the (already magic-byte-validated) bytes to Supabase Storage and
    // returns the public URL the frontend renders in <img> tags. The bucket
    // must be public; the service_role key authorizes the write and is only
    // ever used server-side.
    private String supabaseStore(String filename, String contentType, MultipartFile file) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/storage/v1/object/" + supabaseBucket + "/" + filename))
                .header("Authorization", "Bearer " + supabaseServiceKey)
                .header("Content-Type", contentType)
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(readAllBytes(file)))
                .build();

        if (send(request).statusCode() / 100 != 2) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
        return supabaseUrl + "/storage/v1/object/public/" + supabaseBucket + "/" + filename;
    }

    // Best-effort removal from Supabase Storage, mirroring the disk path's
    // contract — a stray object is far less damaging than a failed DB write.
    private void deleteFromSupabase(String filename) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/storage/v1/object/" + supabaseBucket + "/" + filename))
                .header("Authorization", "Bearer " + supabaseServiceKey)
                .DELETE()
                .build();
        try {
            send(request);
        } catch (ResponseStatusException e) {
            // Cleanup failures are swallowed on purpose.
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }

    private byte[] readAllBytes(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }

    private boolean supabaseEnabled() {
        return !supabaseUrl.isBlank()
                && !supabaseServiceKey.isBlank()
                && !supabaseBucket.isBlank();
    }

    // Tolerate a trailing slash pasted from the Supabase dashboard.
    private static String normalizeSupabaseUrl(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
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
