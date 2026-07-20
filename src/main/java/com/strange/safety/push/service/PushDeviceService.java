package com.strange.safety.push.service;

import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.push.dto.RegisterPushDeviceRequest;
import com.strange.safety.push.entity.PushDevice;
import com.strange.safety.push.repository.PushDeviceRepository;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.entity.UserStatus;
import com.strange.safety.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushDeviceService {

    private final PushDeviceRepository pushDeviceRepository;
    private final UserRepository userRepository;

    @Transactional
    public void register(Long userId, RegisterPushDeviceRequest request) {
        User user = userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String token = request.token().trim();
        PushDevice device = pushDeviceRepository.findByToken(token)
                .orElseGet(() -> PushDevice.create(user, token, request.deviceId(),
                        request.platform(), request.appVersion()));
        device.register(user, request.deviceId(), request.platform(), request.appVersion());
        pushDeviceRepository.save(device);
    }

    @Transactional
    public void deactivate(Long userId, String token) {
        pushDeviceRepository.findByTokenAndUser_Id(token.trim(), userId)
                .ifPresent(PushDevice::deactivate);
    }

    @Transactional
    public void deactivateAllByUserId(Long userId) {
        pushDeviceRepository.findByUser_Id(userId).forEach(PushDevice::deactivate);
    }

    @Transactional
    public void deactivateById(Long deviceId) {
        pushDeviceRepository.findById(deviceId).ifPresent(PushDevice::deactivate);
    }

    public List<PushDevice> findActiveByUserId(Long userId) {
        return pushDeviceRepository.findByUser_IdAndActiveTrue(userId);
    }
}
