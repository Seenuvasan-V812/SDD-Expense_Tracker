package com.dailyexpense.user.outbox;

import com.dailyexpense.shared.outbox.OutboxEntry;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Per-service concrete outbox entity for user-service.
 * Extends the shared-kernel @MappedSuperclass so each service has its own "outbox" table (AL-1).
 */
@Entity
@Table(name = "outbox")
public class UserOutboxEntry extends OutboxEntry {

    public UserOutboxEntry() {
        super();
    }
}
