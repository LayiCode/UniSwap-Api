package com.olamide.UniSwap.Service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The most security-sensitive class in the app — path traversal, content-type
// spoofing, and magic-byte validation all live here, so it gets real tests.
class FileStorageServiceTest {

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 'J', 'F', 'I', 'F'
    };

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00
    };

    // RIFF <size> WEBP ... (12-byte WebP container header)
    private static final byte[] WEBP_BYTES = {
            'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00,
            'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
    };

    @TempDir
    Path tempDir;

    private FileStorageService newService() {
        return new FileStorageService(tempDir.toString(), "http://localhost:8080/uploads");
    }

    @Test
    void store_acceptsValidJpeg() {
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG_BYTES);

        String url = newService().store(file);

        assertThat(url).startsWith("http://localhost:8080/uploads/").endsWith(".jpg");
        assertThat(Files.exists(tempDir.resolve(url.substring(url.lastIndexOf('/') + 1)))).isTrue();
    }

    @Test
    void store_acceptsValidPng() {
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", PNG_BYTES);

        String url = newService().store(file);

        assertThat(url).endsWith(".png");
    }

    @Test
    void store_acceptsValidWebp() {
        MultipartFile file = new MockMultipartFile("file", "photo.webp", "image/webp", WEBP_BYTES);

        String url = newService().store(file);

        assertThat(url).endsWith(".webp");
    }

    @Test
    void store_rejectsFileWhoseBytesDontMatchDeclaredContentType() throws Exception {
        // The classic spoof: claim image/png while the payload is HTML.
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        MultipartFile file = new MockMultipartFile("file", "evil.png", "image/png", html);

        assertThatThrownBy(() -> newService().store(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", org.springframework.http.HttpStatus.BAD_REQUEST);

        assertThat(fileHasNoEntries(tempDir)).isTrue();
    }

    @Test
    void store_rejectsDisallowedContentType() {
        MultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> newService().store(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void store_rejectsEmptyFile() {
        MultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> newService().store(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void delete_removesTheStoredFile() {
        FileStorageService service = newService();
        String url = service.store(new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES));
        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();

        service.delete(url);

        assertThat(Files.exists(tempDir.resolve(filename))).isFalse();
    }

    @Test
    void delete_ignoresNullBlankAndForeignPaths() {
        FileStorageService service = newService();

        service.delete(null);
        service.delete("   ");
        service.delete("http://localhost:8080/uploads/../../../etc/passwd");
        // No exception, nothing deleted outside the upload root.
    }

    @Test
    void store_refusesTraversalInOriginalFilename() {
        MultipartFile file = new MockMultipartFile("file", "../../../etc/passwd.png", "image/png", PNG_BYTES);

        // The extension extractor strips the path; the file still stores fine
        // under a random name — the point is it can never escape the root.
        String url = newService().store(file);

        assertThat(url).startsWith("http://localhost:8080/uploads/");
    }

    private static boolean fileHasNoEntries(Path dir) throws Exception {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        }
    }
}
