package com.rajharita.controller;

import com.rajharita.services.HomeShowcaseService;
import com.rajharita.services.HomeShowcaseService.AboutUsBlockRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ShowcaseController {

    private static final Logger logger = LoggerFactory.getLogger(ShowcaseController.class);
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final String SHOWCASE_URL_PREFIX = "/showcase/";
    private static final String ABOUT_URL_PREFIX = "/about/";

    private final HomeShowcaseService showcaseService;
    private final Path uploadDirectory;
    private final Path aboutUploadDirectory;

    public ShowcaseController(HomeShowcaseService showcaseService,
                              @Value("${app.upload.dir:uploads}") String uploadDirectory) {
        this.showcaseService = showcaseService;
        Path baseDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.uploadDirectory = baseDirectory.resolve("showcase");
        this.aboutUploadDirectory = baseDirectory.resolve("about");
    }

    @GetMapping("/api/showcase/images")
    public List<String> publicImages() {
        return showcaseService.images();
    }

    @GetMapping("/api/showcase/settings")
    public Map<String, Object> publicSettings() {
        Map<String, Object> settings = new java.util.HashMap<>();
        settings.put("images", showcaseService.images());
        settings.put("intervalSeconds", showcaseService.intervalSeconds());
        settings.put("aboutUs", showcaseService.aboutUs());
        return settings;
    }

    @GetMapping("/api/admin/showcase/images")
    public Map<String, Object> adminImages() {
        List<String> images = showcaseService.images();
        return Map.of("images", images, "maximumImages", showcaseService.maximumImages(),
                "intervalSeconds", showcaseService.intervalSeconds());
    }

    @PutMapping("/api/admin/showcase/interval")
    public ResponseEntity<?> updateInterval(@RequestBody IntervalRequest request) {
        try {
            return ResponseEntity.ok(Map.of("intervalSeconds",
                    showcaseService.updateIntervalSeconds(request.intervalSeconds())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/admin/showcase/about-us")
    public Map<String, Object> adminAboutUs() {
        return showcaseService.aboutUs();
    }

    @PutMapping("/api/admin/showcase/about-us")
    public ResponseEntity<?> updateAboutUs(@RequestBody AboutUsRequest request) {
        try {
            return ResponseEntity.ok(showcaseService.updateAboutUs(
                    request.title(), request.summary(), request.content(), request.blocks()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/api/admin/showcase/about-us/images", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadAboutUsImage(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_IMAGE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Choose an image up to 5 MB."));
        }
        String extension = switch (file.getContentType() == null ? "" : file.getContentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> null;
        };
        if (extension == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only JPEG, PNG, and WebP images are supported."));
        }

        String filename = UUID.randomUUID() + extension;
        Path destination = aboutUploadDirectory.resolve(filename).normalize();
        if (!destination.startsWith(aboutUploadDirectory)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid image path."));
        }
        try {
            Files.createDirectories(aboutUploadDirectory);
            file.transferTo(destination);
            return ResponseEntity.ok(Map.of("url", ABOUT_URL_PREFIX + filename));
        } catch (IOException e) {
            deleteUploadedFile(destination);
            return ResponseEntity.internalServerError().body(Map.of("error", "About Us image upload failed."));
        }
    }

    @PostMapping(value = "/api/admin/showcase/images", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadImage(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_IMAGE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Choose an image up to 5 MB."));
        }
        String extension = switch (file.getContentType() == null ? "" : file.getContentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> null;
        };
        if (extension == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only JPEG, PNG, and WebP images are supported."));
        }
        if (showcaseService.images().size() >= showcaseService.maximumImages()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "The showcase can contain up to 10 photos."));
        }

        String filename = UUID.randomUUID() + extension;
        Path destination = uploadDirectory.resolve(filename).normalize();
        if (!destination.startsWith(uploadDirectory)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid image path."));
        }
        try {
            Files.createDirectories(uploadDirectory);
            file.transferTo(destination);
            String imageUrl = SHOWCASE_URL_PREFIX + filename;
            showcaseService.addImage(imageUrl);
            List<String> images = showcaseService.images();
            return ResponseEntity.ok(Map.of("images", images, "maximumImages", showcaseService.maximumImages()));
        } catch (IOException e) {
            deleteUploadedFile(destination);
            return ResponseEntity.internalServerError().body(Map.of("error", "Showcase image upload failed."));
        } catch (RuntimeException e) {
            deleteUploadedFile(destination);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/admin/showcase/images/{filename}")
    public ResponseEntity<?> removeImage(@PathVariable String filename) {
        if (!filename.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp)")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid showcase image."));
        }
        String imageUrl = SHOWCASE_URL_PREFIX + filename;
        Path imagePath = uploadDirectory.resolve(filename).normalize();
        if (!imagePath.startsWith(uploadDirectory)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid image path."));
        }
        try {
            showcaseService.removeImage(imageUrl);
            Files.deleteIfExists(imagePath);
            List<String> images = showcaseService.images();
            return ResponseEntity.ok(Map.of("images", images, "maximumImages", showcaseService.maximumImages()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Photo was removed from the showcase, but its file could not be deleted."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private void deleteUploadedFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException cleanupError) {
            logger.warn("Unable to clean up failed showcase image upload {}", file, cleanupError);
        }
    }

    public record IntervalRequest(Integer intervalSeconds) { }
    public record AboutUsRequest(String title, String summary, String content,
                                 List<AboutUsBlockRequest> blocks) { }
}
