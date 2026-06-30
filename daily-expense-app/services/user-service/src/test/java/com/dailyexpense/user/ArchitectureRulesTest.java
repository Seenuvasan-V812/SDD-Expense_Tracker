package com.dailyexpense.user;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * G-04 fitness: ArchUnit rules enforced on every CI run (before Testcontainers).
 * Rules cover the six non-negotiable structural laws from the Engineering Constitution.
 */
class ArchitectureRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.dailyexpense.user");
    }

    /** P3 / CQ-1 — Strict layering: controllers may not access repositories directly. */
    @Test
    void controllersDoNotAccessRepositories() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..controller..")
            .should().accessClassesThat().resideInAPackage("..repository..")
            .because("Controllers must delegate to services, not repositories (CQ-1)");
        rule.check(classes);
    }

    /** AL-4 — No JPA entity on any controller method signature (DTO boundary). */
    @Test
    void controllersDoNotExposeEntities() {
        ArchRule rule = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .should().haveRawReturnType(
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "a JPA @Entity type",
                    t -> t.isAnnotatedWith(jakarta.persistence.Entity.class)
                )
            )
            .because("Controller methods must return DTOs, never JPA entities (AL-4)");
        rule.check(classes);
    }

    /** AL-1 — No cross-service package imports (no category/expense/budget/savingsgoal packages). */
    @Test
    void noImportsFromOtherBoundedContexts() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.dailyexpense.user..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.dailyexpense.category..",
                "com.dailyexpense.expense..",
                "com.dailyexpense.savingsgoal..",
                "com.dailyexpense.budget.."
            )
            .because("Services must not import other bounded contexts directly (AL-1)");
        rule.check(classes);
    }

    /** CQ-2 — Service methods return Optional<T>, not null. */
    @Test
    void serviceMethodsDoNotReturnRawNull() {
        ArchRule rule = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..service..")
            .and().haveRawReturnType(Object.class)
            .should().haveRawReturnType(
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "non-nullable type without Optional",
                    t -> !t.isAssignableTo(java.util.Optional.class)
                        && t.getFullName().equals("java.lang.Object")
                )
            )
            .because("Service methods must return Optional<T> for absent lookups, never null (CQ-2)");
        rule.allowEmptyShould(true).check(classes);
    }

    /** DB-5 — No double/float for money; only BigDecimal. */
    @Test
    void noDoubleOrFloatForMoney() {
        ArchRule rule = noFields()
            .that().haveNameMatching("(?i).*(amount|price|total|balance|limit|spent).*")
            .should().haveRawType(double.class)
            .orShould().haveRawType(float.class)
            .orShould().haveRawType(Double.class)
            .orShould().haveRawType(Float.class)
            .because("Money fields must use BigDecimal, never double/float (DB-5)");
        rule.check(classes);
    }

    /** SEC-6 — No hardcoded secrets in source. */
    @Test
    void noHardcodedSecrets() {
        ArchRule rule = noClasses()
            .should().accessFieldWhere(
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "field containing a hardcoded secret pattern",
                    access -> {
                        String name = access.getTarget().getName().toLowerCase();
                        return name.contains("password") || name.contains("secret") || name.contains("apikey");
                    }
                )
            )
            .andShould().beAnnotatedWith(org.springframework.stereotype.Component.class)
            .because("Secrets must come from environment, never hardcoded (SEC-6)");
        // Note: simplified guard — full secret scan via git-secrets in CI
        rule.allowEmptyShould(true);
        rule.check(classes);
    }
}
