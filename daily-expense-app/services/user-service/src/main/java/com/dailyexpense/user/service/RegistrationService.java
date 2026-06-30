package com.dailyexpense.user.service;

import com.dailyexpense.shared.exception.BusinessConflictException;
import com.dailyexpense.user.domain.User;
import com.dailyexpense.user.domain.UserStatus;
import com.dailyexpense.user.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Counter usersRegisteredCounter;

    public RegistrationService(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.usersRegisteredCounter = Counter.builder("users.registered")
            .description("Total users successfully registered")
            .register(meterRegistry);
    }

    @Transactional
    public User register(String fullName, String email, String password) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessConflictException("A user with this email already exists");
        }

        // SEC-1: hash before any storage; plaintext never logged or stored
        String hash = passwordEncoder.encode(password);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(hash);
        user.setStatus(UserStatus.INACTIVE_UNVERIFIED);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        User saved = userRepository.save(user);
        usersRegisteredCounter.increment();
        // Log masked email only — no name, no hash, no id (CQ-13)
        log.info("User registered status=INACTIVE_UNVERIFIED");
        return saved;
    }
}
