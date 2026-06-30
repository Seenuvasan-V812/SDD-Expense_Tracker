package com.dailyexpense.user;

import com.dailyexpense.user.dto.UserExportSegment;
import com.dailyexpense.user.port.BudgetUserDataAdapter;
import com.dailyexpense.user.port.CategoryUserDataAdapter;
import com.dailyexpense.user.port.ExpenseUserDataAdapter;
import com.dailyexpense.user.port.LocalUserProfileAdapter;
import com.dailyexpense.user.port.SavingsGoalUserDataAdapter;
import com.dailyexpense.user.port.UserDataPort;
import com.dailyexpense.user.repository.UserRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * T113 — Contract test for all UserDataPort adapters.
 * Guarantees: non-null UserExportSegment for any userId; empty not error for unknown user.
 * ArchUnit: HTTP adapters (Category/Expense/SavingsGoal/Budget) must not access JPA repositories (AL-1).
 */
@ExtendWith(MockitoExtension.class)
class UserDataPortContractTest {

    @Mock
    RestTemplate restTemplate;

    @Mock
    UserRepository userRepository;

    final UUID KNOWN_USER = UUID.randomUUID();
    final UUID UNKNOWN_USER = UUID.randomUUID();

    // ── CategoryUserDataAdapter ───────────────────────────────────────────────

    @Test
    void categoryAdapter_knownUser_returnsNonNullSegment() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"categories\":[{\"id\":\"abc\"}]}");
        UserDataPort adapter = new CategoryUserDataAdapter(restTemplate, "http://localhost:8082");

        UserExportSegment segment = adapter.exportUserData(KNOWN_USER);

        assertThat(segment).isNotNull();
        assertThat(segment.serviceName()).isEqualTo("categories");
        assertThat(segment.jsonContent()).isNotNull();
    }

    @Test
    void categoryAdapter_serviceUnavailable_returnsEmptySegmentNotError() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RestClientException("connection refused"));
        UserDataPort adapter = new CategoryUserDataAdapter(restTemplate, "http://localhost:8082");

        UserExportSegment segment = adapter.exportUserData(UNKNOWN_USER);

        assertThat(segment).isNotNull();
        assertThat(segment.serviceName()).isEqualTo("categories");
        assertThat(segment.jsonContent()).contains("categories");
    }

    // ── ExpenseUserDataAdapter ────────────────────────────────────────────────

    @Test
    void expenseAdapter_knownUser_returnsNonNullSegment() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"expenses\":[]}");
        UserDataPort adapter = new ExpenseUserDataAdapter(restTemplate, "http://localhost:8083");

        UserExportSegment segment = adapter.exportUserData(KNOWN_USER);

        assertThat(segment).isNotNull();
        assertThat(segment.serviceName()).isEqualTo("expenses");
    }

    @Test
    void expenseAdapter_serviceUnavailable_returnsEmptySegmentNotError() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RestClientException("connection refused"));
        UserDataPort adapter = new ExpenseUserDataAdapter(restTemplate, "http://localhost:8083");

        UserExportSegment segment = adapter.exportUserData(UNKNOWN_USER);

        assertThat(segment).isNotNull();
        assertThat(segment.jsonContent()).contains("expenses");
    }

    // ── SavingsGoalUserDataAdapter ────────────────────────────────────────────

    @Test
    void savingsGoalAdapter_knownUser_returnsNonNullSegment() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"savingsGoals\":[]}");
        UserDataPort adapter = new SavingsGoalUserDataAdapter(restTemplate, "http://localhost:8084");

        UserExportSegment segment = adapter.exportUserData(KNOWN_USER);

        assertThat(segment).isNotNull();
        assertThat(segment.serviceName()).isEqualTo("savingsGoals");
    }

    @Test
    void savingsGoalAdapter_serviceUnavailable_returnsEmptySegmentNotError() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RestClientException("connection refused"));
        UserDataPort adapter = new SavingsGoalUserDataAdapter(restTemplate, "http://localhost:8084");

        UserExportSegment segment = adapter.exportUserData(UNKNOWN_USER);

        assertThat(segment).isNotNull();
        assertThat(segment.jsonContent()).contains("savingsGoals");
    }

    // ── BudgetUserDataAdapter ─────────────────────────────────────────────────

    @Test
    void budgetAdapter_knownUser_returnsNonNullSegment() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"budgets\":[]}");
        UserDataPort adapter = new BudgetUserDataAdapter(restTemplate, "http://localhost:8085");

        UserExportSegment segment = adapter.exportUserData(KNOWN_USER);

        assertThat(segment).isNotNull();
        assertThat(segment.serviceName()).isEqualTo("budgets");
    }

    @Test
    void budgetAdapter_serviceUnavailable_returnsEmptySegmentNotError() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RestClientException("connection refused"));
        UserDataPort adapter = new BudgetUserDataAdapter(restTemplate, "http://localhost:8085");

        UserExportSegment segment = adapter.exportUserData(UNKNOWN_USER);

        assertThat(segment).isNotNull();
        assertThat(segment.jsonContent()).contains("budgets");
    }

    // ── LocalUserProfileAdapter ───────────────────────────────────────────────

    @Test
    void localProfileAdapter_knownUser_returnsNonNullSegment() {
        var user = new com.dailyexpense.user.domain.User();
        user.setFullName("Alice");
        user.setStatus(com.dailyexpense.user.domain.UserStatus.ACTIVE);
        when(userRepository.findById(KNOWN_USER)).thenReturn(Optional.of(user));
        UserDataPort adapter = new LocalUserProfileAdapter(userRepository);

        UserExportSegment segment = adapter.exportUserData(KNOWN_USER);

        assertThat(segment).isNotNull();
        assertThat(segment.serviceName()).isEqualTo("profile");
        assertThat(segment.jsonContent()).contains("Alice");
    }

    @Test
    void localProfileAdapter_unknownUser_returnsEmptySegmentNotError() {
        when(userRepository.findById(UNKNOWN_USER)).thenReturn(Optional.empty());
        UserDataPort adapter = new LocalUserProfileAdapter(userRepository);

        UserExportSegment segment = adapter.exportUserData(UNKNOWN_USER);

        assertThat(segment).isNotNull();
        assertThat(segment.serviceName()).isEqualTo("profile");
    }

    // ── ArchUnit: AL-1 — HTTP adapters must not access JPA repositories ────────

    @Test
    void httpAdapters_doNotAccessJpaRepositories() {
        JavaClasses adapterClasses = new ClassFileImporter()
            .importClasses(
                CategoryUserDataAdapter.class,
                ExpenseUserDataAdapter.class,
                SavingsGoalUserDataAdapter.class,
                BudgetUserDataAdapter.class
            );

        ArchRule rule = noClasses()
            .should().accessClassesThat().resideInAPackage("..repository..")
            .because("HTTP-based adapters must not access JPA repositories (AL-1); use HTTP only");

        rule.check(adapterClasses);
    }
}
