package com.dailyexpense.shared;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * G-04 fitness rules for shared-kernel itself (T003).
 * Verifies the kernel carries zero domain logic and zero repositories.
 */
class ArchitectureRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.dailyexpense.shared");
    }

    /** Shared-kernel must not contain any JPA @Repository beans. */
    @Test
    void sharedKernelHasNoRepositories() {
        noClasses()
            .should().beAnnotatedWith(org.springframework.stereotype.Repository.class)
            .allowEmptyShould(true)
            .check(classes);
    }

    /** Shared-kernel must not contain any @Service beans (no domain logic). */
    @Test
    void sharedKernelHasNoServiceBeans() {
        noClasses()
            .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
            .allowEmptyShould(true)
            .check(classes);
    }

    /** No domain entity classes in shared-kernel — only MappedSuperclass is allowed. */
    @Test
    void sharedKernelHasNoTopLevelEntities() {
        noClasses()
            .that().resideInAPackage("com.dailyexpense.shared..")
            .should().beAnnotatedWith(jakarta.persistence.Entity.class)
            .allowEmptyShould(true)
            .check(classes);
    }

    /** No double/float fields for anything named 'amount' (DB-5). */
    @Test
    void noDoubleOrFloatForMoney() {
        noFields()
            .that().haveNameMatching("(?i).*(amount|price|total|balance|limit|spent).*")
            .should().haveRawType(double.class)
            .orShould().haveRawType(float.class)
            .orShould().haveRawType(Double.class)
            .orShould().haveRawType(Float.class)
            .allowEmptyShould(true)
            .check(classes);
    }

    /** Shared-kernel must not import any bounded context (service-specific) packages. */
    @Test
    void noServiceSpecificImports() {
        noClasses()
            .that().resideInAPackage("com.dailyexpense.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.dailyexpense.user..",
                "com.dailyexpense.category..",
                "com.dailyexpense.expense..",
                "com.dailyexpense.savingsgoal..",
                "com.dailyexpense.budget.."
            )
            .allowEmptyShould(true)
            .check(classes);
    }
}
