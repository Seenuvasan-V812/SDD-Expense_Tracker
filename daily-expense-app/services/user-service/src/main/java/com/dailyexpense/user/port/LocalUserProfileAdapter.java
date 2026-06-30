package com.dailyexpense.user.port;

import com.dailyexpense.user.domain.User;
import com.dailyexpense.user.dto.UserExportSegment;
import com.dailyexpense.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * T112 — Reads the user's own profile directly from identity_db.
 * Only this adapter may access identity_db; others use HTTP (AL-1).
 */
@Component
public class LocalUserProfileAdapter implements UserDataPort {

    private final UserRepository userRepository;

    public LocalUserProfileAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserExportSegment exportUserData(UUID userId) {
        return userRepository.findById(userId)
                .map(this::toJson)
                .orElse(new UserExportSegment("profile", "{}"));
    }

    private UserExportSegment toJson(User user) {
        String json = String.format(
                "{\"fullName\":\"%s\",\"status\":\"%s\"}",
                user.getFullName(), user.getStatus().name());
        return new UserExportSegment("profile", json);
    }
}
