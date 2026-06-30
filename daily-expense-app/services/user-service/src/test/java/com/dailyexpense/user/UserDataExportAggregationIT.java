package com.dailyexpense.user;

import com.dailyexpense.user.domain.DataExportStatus;
import com.dailyexpense.user.dto.DataExportResponse;
import com.dailyexpense.user.dto.UserExportSegment;
import com.dailyexpense.user.port.CategoryUserDataAdapter;
import com.dailyexpense.user.port.BudgetUserDataAdapter;
import com.dailyexpense.user.port.ExpenseUserDataAdapter;
import com.dailyexpense.user.port.SavingsGoalUserDataAdapter;
import com.dailyexpense.user.service.DataExportAggregatorService;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * T112 — DataExportAggregator path:
 * fans out to all 4 service adapters + local profile, builds ZIP, uploads to MinIO,
 * sets export status READY, writes DataExportReadyEvent to outbox in same tx (CQ-8).
 *
 * Adapters are @MockBean; MinioClient is @MockBean (no real MinIO in user-service IT).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserDataExportAggregationIT extends AbstractUserServiceIT {

    @MockBean
    private CategoryUserDataAdapter categoryUserDataAdapter;

    @MockBean
    private ExpenseUserDataAdapter expenseUserDataAdapter;

    @MockBean
    private SavingsGoalUserDataAdapter savingsGoalUserDataAdapter;

    @MockBean
    private BudgetUserDataAdapter budgetUserDataAdapter;

    @MockBean
    private MinioClient minioClient;

    @Autowired
    private DataExportAggregatorService aggregatorService;

    final String EMAIL = "agg-owner+" + UUID.randomUUID() + "@example.com";
    final String PASSWORD = "AggPass99!";

    UUID ownerId;

    @BeforeAll
    void setup() throws Exception {
        when(categoryUserDataAdapter.exportUserData(any()))
                .thenReturn(new UserExportSegment("categories", "{\"categories\":[]}"));
        when(expenseUserDataAdapter.exportUserData(any()))
                .thenReturn(new UserExportSegment("expenses", "{\"expenses\":[]}"));
        when(savingsGoalUserDataAdapter.exportUserData(any()))
                .thenReturn(new UserExportSegment("savingsGoals", "{\"savingsGoals\":[]}"));
        when(budgetUserDataAdapter.exportUserData(any()))
                .thenReturn(new UserExportSegment("budgets", "{\"budgets\":[]}"));

        when(minioClient.bucketExists(ArgumentMatchers.any())).thenReturn(true);

        registerAndActivate("Agg Owner", EMAIL, PASSWORD);
        ownerId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
    }

    @Test
    void aggregation_setsExportStatusToReady() throws Exception {
        // Create export (REQUESTED)
        var tokens = loginTokens(EMAIL, PASSWORD);
        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(tokens.accessToken()));
        ResponseEntity<DataExportResponse> resp = rest.exchange(
                usersBase() + "/me/data-export", HttpMethod.POST, req, DataExportResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID exportId = resp.getBody().exportId();

        // Trigger aggregation synchronously
        aggregatorService.runAggregation(exportId, ownerId);

        // Verify status changed to READY
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM data_exports WHERE id = ?", String.class, exportId);
        assertThat(status).isEqualTo(DataExportStatus.READY.name());
    }

    @Test
    void aggregation_setsDownloadRef() throws Exception {
        var tokens = loginTokens(EMAIL, PASSWORD);
        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(tokens.accessToken()));
        ResponseEntity<DataExportResponse> resp = rest.exchange(
                usersBase() + "/me/data-export", HttpMethod.POST, req, DataExportResponse.class);

        UUID exportId = resp.getBody().exportId();
        aggregatorService.runAggregation(exportId, ownerId);

        String downloadRef = jdbcTemplate.queryForObject(
                "SELECT download_ref FROM data_exports WHERE id = ?", String.class, exportId);
        assertThat(downloadRef).isNotNull().contains(exportId.toString());
    }

    @Test
    void aggregation_writesDataExportReadyEventToOutbox() throws Exception {
        var tokens = loginTokens(EMAIL, PASSWORD);
        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(tokens.accessToken()));
        ResponseEntity<DataExportResponse> resp = rest.exchange(
                usersBase() + "/me/data-export", HttpMethod.POST, req, DataExportResponse.class);

        UUID exportId = resp.getBody().exportId();
        int countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type = 'DataExportReadyEvent'",
                Integer.class);

        aggregatorService.runAggregation(exportId, ownerId);

        int countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type = 'DataExportReadyEvent'",
                Integer.class);
        assertThat(countAfter).isGreaterThan(countBefore);
    }

    @Test
    void download_afterAggregation_returns200ForOwner() throws Exception {
        var tokens = loginTokens(EMAIL, PASSWORD);
        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(tokens.accessToken()));
        ResponseEntity<DataExportResponse> resp = rest.exchange(
                usersBase() + "/me/data-export", HttpMethod.POST, req, DataExportResponse.class);

        UUID exportId = resp.getBody().exportId();
        aggregatorService.runAggregation(exportId, ownerId);

        // Download should return 200 with the storage key as body
        ResponseEntity<String> dlResp = rest.exchange(
                usersBase() + "/me/data-export/" + exportId + "/download",
                HttpMethod.GET, req, String.class);
        assertThat(dlResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
