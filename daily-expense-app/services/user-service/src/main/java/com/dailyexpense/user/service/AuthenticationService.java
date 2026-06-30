package com.dailyexpense.user.service;

import com.dailyexpense.shared.security.JwtService;
import com.dailyexpense.user.domain.User;
import com.dailyexpense.user.domain.UserStatus;
import com.dailyexpense.user.dto.AuthTokenResponse;
import com.dailyexpense.user.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRotationService tokenRotationService;

    public AuthenticationService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 JwtService jwtService,
                                 TokenRotationService tokenRotationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenRotationService = tokenRotationService;
    }

    @Transactional
    public AuthTokenResponse login(String email, String password) {
        // Use the same generic message for all failure reasons to prevent enumeration (SEC-3)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // password logged masked only — SEC-1: plaintext never logged
            throw new BadCredentialsException("Invalid credentials");
        }

        // INACTIVE_UNVERIFIED and DELETED both → generic 401, never 403 and never "email not verified"
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String accessToken = jwtService.issueAccessToken(user.getId());
        String rawRefreshToken = jwtService.issueRefreshToken(user.getId());
        UUID familyId = UUID.randomUUID();
        tokenRotationService.storeNewToken(user.getId(), familyId, rawRefreshToken);

        return new AuthTokenResponse(accessToken, rawRefreshToken, 900L);
    }
}
