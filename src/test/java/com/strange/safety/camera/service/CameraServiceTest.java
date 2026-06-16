package com.strange.safety.camera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.strange.safety.auth.entity.Role;
import com.strange.safety.camera.dto.CameraResponse;
import com.strange.safety.camera.dto.CreateCameraRequest;
import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraSourceType;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.common.util.AesUtil;
import com.strange.safety.facility.entity.AccessType;
import com.strange.safety.facility.entity.Facility;
import com.strange.safety.facility.entity.UserFacility;
import com.strange.safety.facility.repository.FacilityRepository;
import com.strange.safety.facility.repository.UserFacilityRepository;
import com.strange.safety.facility.service.FacilityService;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CameraServiceTest {

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private UserFacilityRepository userFacilityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FacilityService facilityService;

    @Mock
    private AesUtil aesUtil;

    @Mock
    private VirtualCameraPoolService virtualCameraPoolService;

    @Mock
    private RtspSimulationService rtspSimulationService;

    private CameraService cameraService;

    @BeforeEach
    void setUp() {
        cameraService = new CameraService(
                cameraRepository,
                facilityRepository,
                userFacilityRepository,
                userRepository,
                facilityService,
                aesUtil,
                virtualCameraPoolService,
                rtspSimulationService
        );
    }

    private CreateCameraRequest createRequest() {
        CreateCameraRequest request = new CreateCameraRequest();
        request.setCameraName("Test Camera");
        request.setCameraSerialNumber("SN123456");
        request.setCameraLoginId("admin");
        request.setCameraPassword("pw123");
        request.setRtspUrl("rtsp://example.com/stream");
        request.setLocationDescription("Main Room");
        request.setAiEnabled(true);
        request.setSourceType(CameraSourceType.REAL_RTSP);
        return request;
    }

    @Test
    void createCameraWithFacilityIdResolvesCorrectly() {
        Long userId = 1L;
        Long facilityId = 100L;
        CreateCameraRequest request = createRequest();

        Facility facility = Facility.builder().facilityName("Test Facility").build();
        ReflectionTestUtils.setField(facility, "id", facilityId);

        when(facilityService.getFacilityWithOwnerCheck(userId, facilityId)).thenReturn(facility);
        when(aesUtil.encrypt("pw123")).thenReturn("encrypted_pw123");
        when(cameraRepository.save(any(Camera.class))).thenAnswer(invocation -> {
            Camera camera = invocation.getArgument(0);
            ReflectionTestUtils.setField(camera, "id", 500L);
            return camera;
        });

        CameraResponse response = cameraService.createCamera(userId, facilityId, request);

        assertThat(response.getCameraId()).isEqualTo(500L);
        assertThat(response.getCameraName()).isEqualTo("Test Camera");
        verify(facilityRepository, never()).findActiveFacilitiesByManagerId(any(), any(), any());
    }

    @Test
    void createCameraWithNullFacilityIdResolvesDefaultFacility() {
        Long userId = 1L;
        Long defaultFacilityId = 200L;
        CreateCameraRequest request = createRequest();

        Facility facility = Facility.builder().facilityName("Default Facility").build();
        ReflectionTestUtils.setField(facility, "id", defaultFacilityId);

        Page<Facility> page = new PageImpl<>(List.of(facility));
        when(facilityRepository.findActiveFacilitiesByManagerId(
                eq(userId),
                eq(AccessType.MANAGER),
                eq(PageRequest.of(0, 1))
        )).thenReturn(page);

        when(facilityService.getFacilityWithOwnerCheck(userId, defaultFacilityId)).thenReturn(facility);
        when(aesUtil.encrypt("pw123")).thenReturn("encrypted_pw123");
        when(cameraRepository.save(any(Camera.class))).thenAnswer(invocation -> {
            Camera camera = invocation.getArgument(0);
            ReflectionTestUtils.setField(camera, "id", 500L);
            return camera;
        });

        CameraResponse response = cameraService.createCamera(userId, null, request);

        assertThat(response.getCameraId()).isEqualTo(500L);
        assertThat(response.getCameraName()).isEqualTo("Test Camera");
        verify(facilityRepository, times(1)).findActiveFacilitiesByManagerId(eq(userId), eq(AccessType.MANAGER), eq(PageRequest.of(0, 1)));
    }

    @Test
    void createCameraWithNullFacilityIdAutoCreatesFacilityForIndividual() {
        Long userId = 1L;
        Long autoFacilityId = 300L;
        CreateCameraRequest request = createRequest();

        User user = User.create("test@example.com", "hash", "IndividualUser", "010-1234-5678", Role.INDIVIDUAL);
        ReflectionTestUtils.setField(user, "id", userId);

        Facility facility = Facility.builder().facilityName("IndividualUser 보호 시설").build();
        ReflectionTestUtils.setField(facility, "id", autoFacilityId);

        when(facilityRepository.findActiveFacilitiesByManagerId(
                eq(userId),
                eq(AccessType.MANAGER),
                eq(PageRequest.of(0, 1))
        )).thenReturn(Page.empty());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(facilityRepository.save(any(Facility.class))).thenReturn(facility);
        when(userFacilityRepository.save(any(UserFacility.class))).thenReturn(null);

        when(facilityService.getFacilityWithOwnerCheck(userId, autoFacilityId)).thenReturn(facility);
        when(aesUtil.encrypt("pw123")).thenReturn("encrypted_pw123");
        when(cameraRepository.save(any(Camera.class))).thenAnswer(invocation -> {
            Camera camera = invocation.getArgument(0);
            ReflectionTestUtils.setField(camera, "id", 500L);
            return camera;
        });

        CameraResponse response = cameraService.createCamera(userId, null, request);

        assertThat(response.getCameraId()).isEqualTo(500L);
        assertThat(response.getCameraName()).isEqualTo("Test Camera");
        verify(facilityRepository, times(1)).save(any(Facility.class));
        verify(userFacilityRepository, times(1)).save(any(UserFacility.class));
    }

    @Test
    void createCameraWithNullFacilityIdThrowsForCorporateWhenNoFacilityFound() {
        Long userId = 1L;
        CreateCameraRequest request = createRequest();

        User user = User.create("corp@example.com", "hash", "CorpUser", "010-1234-5678", Role.CORPORATE);
        ReflectionTestUtils.setField(user, "id", userId);

        when(facilityRepository.findActiveFacilitiesByManagerId(
                eq(userId),
                eq(AccessType.MANAGER),
                eq(PageRequest.of(0, 1))
        )).thenReturn(Page.empty());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> cameraService.createCamera(userId, null, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FACILITY_NOT_FOUND);
    }

    @Test
    void getCamerasWithFacilityIdQueriesCorrectly() {
        Long userId = 1L;
        Long facilityId = 100L;

        Facility facility = Facility.builder().facilityName("Test Facility").build();
        ReflectionTestUtils.setField(facility, "id", facilityId);

        Camera camera = Camera.builder()
                .facility(facility)
                .cameraName("Test Camera")
                .cameraSerialNumber("SN123")
                .build();
        ReflectionTestUtils.setField(camera, "id", 500L);

        when(facilityService.getFacilityWithOwnerCheck(userId, facilityId)).thenReturn(facility);
        when(cameraRepository.findByFacility_IdAndStatus(facilityId, CameraStatus.ACTIVE))
                .thenReturn(List.of(camera));

        List<CameraResponse> responses = cameraService.getCameras(userId, facilityId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCameraId()).isEqualTo(500L);
        verify(facilityRepository, never()).findActiveFacilitiesByManagerId(any(), any(), any());
    }

    @Test
    void getCamerasWithNullFacilityIdQueriesDefaultFacility() {
        Long userId = 1L;
        Long defaultFacilityId = 200L;

        Facility facility = Facility.builder().facilityName("Default Facility").build();
        ReflectionTestUtils.setField(facility, "id", defaultFacilityId);

        Page<Facility> page = new PageImpl<>(List.of(facility));
        when(facilityRepository.findActiveFacilitiesByManagerId(
                eq(userId),
                eq(AccessType.MANAGER),
                eq(PageRequest.of(0, 1))
        )).thenReturn(page);

        Camera camera = Camera.builder()
                .facility(facility)
                .cameraName("Test Camera")
                .cameraSerialNumber("SN123")
                .build();
        ReflectionTestUtils.setField(camera, "id", 500L);

        when(facilityService.getFacilityWithOwnerCheck(userId, defaultFacilityId)).thenReturn(facility);
        when(cameraRepository.findByFacility_IdAndStatus(defaultFacilityId, CameraStatus.ACTIVE))
                .thenReturn(List.of(camera));

        List<CameraResponse> responses = cameraService.getCameras(userId, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCameraId()).isEqualTo(500L);
        verify(facilityRepository, times(1)).findActiveFacilitiesByManagerId(eq(userId), eq(AccessType.MANAGER), eq(PageRequest.of(0, 1)));
    }

    @Test
    void getCamerasWithNullFacilityIdReturnsEmptyListForIndividualWhenNoFacilityFound() {
        Long userId = 1L;
        User user = User.create("test@example.com", "hash", "IndividualUser", "010-1234-5678", Role.INDIVIDUAL);
        ReflectionTestUtils.setField(user, "id", userId);

        when(facilityRepository.findActiveFacilitiesByManagerId(
                eq(userId),
                eq(AccessType.MANAGER),
                eq(PageRequest.of(0, 1))
        )).thenReturn(Page.empty());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        List<CameraResponse> responses = cameraService.getCameras(userId, null);

        assertThat(responses).isEmpty();
        verify(facilityService, never()).getFacilityWithOwnerCheck(any(), any());
    }

    @Test
    void getCamerasWithNullFacilityIdThrowsForCorporateWhenNoFacilityFound() {
        Long userId = 1L;
        User user = User.create("corp@example.com", "hash", "CorpUser", "010-1234-5678", Role.CORPORATE);
        ReflectionTestUtils.setField(user, "id", userId);

        when(facilityRepository.findActiveFacilitiesByManagerId(
                eq(userId),
                eq(AccessType.MANAGER),
                eq(PageRequest.of(0, 1))
        )).thenReturn(Page.empty());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> cameraService.getCameras(userId, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FACILITY_NOT_FOUND);
    }
}
