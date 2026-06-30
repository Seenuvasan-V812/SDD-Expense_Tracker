package com.dailyexpense.user.port;

import com.dailyexpense.user.dto.UserExportSegment;

import java.util.UUID;

/**
 * T112 — Anti-corruption port for fetching a user's data from one bounded context.
 * Each implementation MUST NOT access any other service's database directly (AL-1).
 * Returns an empty segment (not error) when the user has no data in that context.
 */
public interface UserDataPort {

    UserExportSegment exportUserData(UUID userId);
}
