package com.rishank.securedigitallocker.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.rishank.securedigitallocker.model.User;
import com.rishank.securedigitallocker.repository.UserRepository;

class MfaServiceTests {

    @Test
    void locksMfaAfterFiveInvalidCodes() {
        UserRepository userRepository = mock(UserRepository.class);
        MfaService mfaService = new MfaService(userRepository);
        User user = new User();
        user.setEmail("owner@example.com");
        user.setMfaEnabled(true);
        user.setMfaSecret("JBSWY3DPEHPK3PXP");
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertFalse(mfaService.verify(user, "invalid"));
        }

        assertTrue(user.getMfaLockedUntil().isAfter(LocalDateTime.now()));
        assertFalse(mfaService.verify(user, "123456"));
        verify(userRepository, org.mockito.Mockito.times(5)).save(user);
    }

    @Test
    void locksMfaSetupAfterFiveInvalidCodes() {
        UserRepository userRepository = mock(UserRepository.class);
        MfaService mfaService = new MfaService(userRepository);
        User user = new User();
        user.setEmail("owner@example.com");
        user.setMfaSecret("JBSWY3DPEHPK3PXP");
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(IllegalArgumentException.class, () -> mfaService.enable(user, "invalid"));
        }

        assertTrue(user.getMfaLockedUntil().isAfter(LocalDateTime.now()));
        verify(userRepository, org.mockito.Mockito.times(5)).save(user);
    }
}
