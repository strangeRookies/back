package com.strange.safety.camera.overlay;

import com.strange.safety.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AiOverlayController {

    private final AiOverlayRegistryService registryService;

    @GetMapping("/api/cameras/{cameraLoginId}/ai-overlay")
    public ResponseEntity<ApiResponse<AiOverlayResponse>> get(@PathVariable String cameraLoginId) {
        return ResponseEntity.ok(ApiResponse.success(registryService.get(cameraLoginId)));
    }

    @PostMapping("/api/cameras/{cameraLoginId}/ai-overlay/start")
    public ResponseEntity<ApiResponse<AiOverlayResponse>> start(@PathVariable String cameraLoginId) {
        return ResponseEntity.ok(ApiResponse.success(registryService.requestStart(cameraLoginId)));
    }

    @PostMapping("/api/cameras/{cameraLoginId}/ai-overlay/stop")
    public ResponseEntity<ApiResponse<AiOverlayResponse>> stop(@PathVariable String cameraLoginId) {
        return ResponseEntity.ok(ApiResponse.success(registryService.stop(cameraLoginId)));
    }

    @PostMapping("/api/internal/ai-overlays/report")
    public ResponseEntity<ApiResponse<AiOverlayResponse>> report(@Valid @RequestBody AiOverlayReportRequest request) {
        return ResponseEntity.ok(ApiResponse.success(registryService.report(request)));
    }
}
