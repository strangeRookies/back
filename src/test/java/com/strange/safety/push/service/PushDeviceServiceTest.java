package com.strange.safety.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strange.safety.auth.entity.Role;
import com.strange.safety.push.dto.RegisterPushDeviceRequest;
import com.strange.safety.push.entity.PushDevice;
import com.strange.safety.push.entity.PushPlatform;
import com.strange.safety.push.repository.PushDeviceRepository;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.entity.UserStatus;
import com.strange.safety.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushDeviceServiceTest {

    @Mock PushDeviceRepository pushDeviceRepository;
    @Mock UserRepository userRepository;

    @Test
    void registerCreatesDeviceWithoutExposingTokenInResponse() {
        User user = user("user@example.com");
        when(userRepository.findByIdAndStatus(1L, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(pushDeviceRepository.findByToken("token-1")).thenReturn(Optional.empty());
        PushDeviceService service = new PushDeviceService(pushDeviceRepository, userRepository);

        service.register(1L, new RegisterPushDeviceRequest(
                " token-1 ", "device-1", PushPlatform.ANDROID, "1.0.0"));

        ArgumentCaptor<PushDevice> captor = ArgumentCaptor.forClass(PushDevice.class);
        verify(pushDeviceRepository).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("token-1");
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getUser()).isSameAs(user);
    }

    @Test
    void registerReassignsExistingTokenToCurrentUser() {
        User previousUser = user("previous@example.com");
        User currentUser = user("current@example.com");
        PushDevice existing = PushDevice.create(
                previousUser, "token-1", "old-device", PushPlatform.ANDROID, "0.9.0");
        existing.deactivate();
        when(userRepository.findByIdAndStatus(2L, UserStatus.ACTIVE)).thenReturn(Optional.of(currentUser));
        when(pushDeviceRepository.findByToken("token-1")).thenReturn(Optional.of(existing));
        PushDeviceService service = new PushDeviceService(pushDeviceRepository, userRepository);

        service.register(2L, new RegisterPushDeviceRequest(
                "token-1", "new-device", PushPlatform.ANDROID, "1.0.0"));

        assertThat(existing.getUser()).isSameAs(currentUser);
        assertThat(existing.getDeviceId()).isEqualTo("new-device");
        assertThat(existing.isActive()).isTrue();
        verify(pushDeviceRepository).save(existing);
    }

    @Test
    void deactivateIsIdempotentAndLimitedToOwner() {
        PushDeviceRepository repository = pushDeviceRepository;
        PushDeviceService service = new PushDeviceService(repository, userRepository);
        when(repository.findByTokenAndUser_Id("token-1", 1L)).thenReturn(Optional.empty());

        service.deactivate(1L, " token-1 ");

        verify(repository).findByTokenAndUser_Id("token-1", 1L);
    }

    private User user(String email) {
        return User.create(email, "hash", "사용자", "01012345678", Role.INDIVIDUAL);
    }
}
