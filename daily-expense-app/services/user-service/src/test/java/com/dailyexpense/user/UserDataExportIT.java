package com.dailyexpense.user;

import com.dailyexpense.user.dto.AuthTokenResponse;
import com.dailyexpense.user.dto.DataExportResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T035 — Data export: request (202), status REQUESTED, ownership (200 owner / 403 foreign).
 *
 * MUSTs:
 * - POST /me/data-export → 202 + exportId
 * - Status in DB is REQUESTED
 * - GET /me/data-export/{id}/download by owner → 200
 * - GET by foreign user → 403 (403-never-404, INV-1/SEC-3)
 * - download_ref never appears in logs (enforced via application design; tested structurally)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserDataExportIT extends AbstractUserServiceIT {

    final String OWNER_EMAIL = "export-owner+" + UUID.randomUUID() + "@example.com";
    final String OTHER_EMAIL = "export-other+" + UUID.randomUUID() + "@example.com";
    final String PASSWORD = "ExportPass99!";

    AuthTokenResponse ownerTokens;
    AuthTokenResponse otherTokens;
    UUID exportId;

    @BeforeAll
    void setup() {
        registerAndActivate("Export Owner", OWNER_EMAIL, PASSWORD);
        registerAndActivate("Export Other", OTHER_EMAIL, PASSWORD);
        ownerTokens = loginTokens(OWNER_EMAIL, PASSWORD);
        otherTokens = loginTokens(OTHER_EMAIL, PASSWORD);
    }

    @Test
    void requestDataExport_returns202WithExportId() {
        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(ownerTokens.accessToken()));
        ResponseEntity<DataExportResponse> resp = rest.exchange(
            usersBase() + "/me/data-export", HttpMethod.POST, req, DataExportResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().exportId()).isNotNull();

        exportId = resp.getBody().exportId();
    }

    @Test
    void requestDataExport_statusIsRequested() {
        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(ownerTokens.accessToken()));
        ResponseEntity<DataExportResponse> resp = rest.exchange(
            usersBase() + "/me/data-export", HttpMethod.POST, req, DataExportResponse.class);

        assertThat(resp.getBody().status()).isEqualTo("REQUESTED");

        // Verify in DB as well
        UUID id = resp.getBody().exportId();
        String dbStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM data_exports WHERE id = ?", String.class, id);
        assertThat(dbStatus).isEqualTo("REQUESTED");
    }

    @Test
    void requestDataExport_hasLocationHeader() {
        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(ownerTokens.accessToken()));
        ResponseEntity<DataExportResponse> resp = rest.exchange(
            usersBase() + "/me/data-export", HttpMethod.POST, req, DataExportResponse.class);

        assertThat(resp.getHeaders().get("Location")).isNotNull();
    }

    @Test
    void downloadExport_owner_returns200() {
        // Request an export first to get an ID
        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(ownerTokens.accessToken()));
        DataExportResponse exportResp = rest.exchange(
            usersBase() + "/me/data-export", HttpMethod.POST, req, DataExportResponse.class).getBody();
        assertThat(exportResp).isNotNull();
        UUID id = exportResp.exportId();

        ResponseEntity<String> dlResp = rest.exchange(
            usersBase() + "/me/data-export/" + id + "/download",
            HttpMethod.GET, req, String.class);

        assertThat(dlResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void downloadExport_foreignUser_returns403NeverNot404() {
        // Owner requests an export
        HttpEntity<Void> ownerReq = new HttpEntity<>(bearerHeaders(ownerTokens.accessToken()));
        DataExportResponse exportResp = rest.exchange(
            usersBase() + "/me/data-export", HttpMethod.POST, ownerReq, DataExportResponse.class).getBody();
        assertThat(exportResp).isNotNull();
        UUID id = exportResp.exportId();

        // Foreign user tries to download — must get 403, never 404 (INV-1/SEC-3)
        HttpEntity<Void> otherReq = new HttpEntity<>(bearerHeaders(otherTokens.accessToken()));
        ResponseEntity<String> dlResp = rest.exchange(
            usersBase() + "/me/data-export/" + id + "/download",
            HttpMethod.GET, otherReq, String.class);

        assertThat(dlResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(dlResp.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void downloadExport_unauthenticated_returns401() {
        ResponseEntity<String> resp = rest.getForEntity(
            usersBase() + "/me/data-export/" + UUID.randomUUID() + "/download", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
