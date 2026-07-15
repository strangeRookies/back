package com.strange.safety.vlm.snapshotassist;

import com.strange.safety.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class SnapshotAssistController {
    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final SnapshotAssistService service;

    /**
     * Edge AI uploads event-frame JPEG after primary MQTT alert publish.
     * Auth: shared service token only (not end-user JWT).
     */
    @PostMapping("/api/internal/vlm/snapshot-assist/{eventId}")
    public ResponseEntity<?> submit(
            @PathVariable String eventId,
            @RequestHeader(value = SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @RequestParam(value = "cameraLoginId", required = false) String cameraLoginId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (!service.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("SNAPSHOT_ASSIST_DISABLED", "snapshot assist disabled"));
        }
        if (!service.isServiceTokenValid(serviceToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "invalid service token"));
        }
        if (eventId == null || eventId.trim().isEmpty() || eventId.contains("..") || eventId.contains("/") || eventId.contains("\\")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("BAD_REQUEST", "invalid or empty eventId"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("BAD_REQUEST", "empty file payload"));
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg") && !contentType.equalsIgnoreCase("image/jpg"))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("BAD_REQUEST", "Only image/jpeg is allowed"));
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("BAD_REQUEST", "invalid file extension"));
            }
        }
        SnapshotAssistRecord record = service.submit(eventId, cameraLoginId, file.getBytes());
        return ResponseEntity.accepted().body(ApiResponse.success(toMap(record)));
    }

    /** Dashboard / operator read of assist status by eventId (JWT protected). */
    @GetMapping("/api/vlm/snapshot-assist/{eventId}")
    public ResponseEntity<?> get(@PathVariable String eventId) {
        return service.get(eventId)
                .<ResponseEntity<?>>map(rec -> ResponseEntity.ok(ApiResponse.success(toMap(rec))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("NOT_FOUND", "not found")));
    }

    private static Map<String, Object> toMap(SnapshotAssistRecord rec) {
        return Map.of(
                "eventId", rec.eventId(),
                "cameraLoginId", rec.cameraLoginId() == null ? "" : rec.cameraLoginId(),
                "status", rec.status().name(),
                "summaryKo", rec.summaryKo() == null ? "" : rec.summaryKo(),
                "errorMessage", rec.errorMessage() == null ? "" : rec.errorMessage(),
                "updatedAt", rec.updatedAt() == null ? "" : rec.updatedAt().toString()
        );
    }
}
