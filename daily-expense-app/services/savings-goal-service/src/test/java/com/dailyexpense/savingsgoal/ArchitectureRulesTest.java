package com.dailyexpense.savingsgoal;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * G-04 fitness: ArchUnit rules for savings-goal-service (scaffolded; grows with implementation).
 */
class ArchitectureRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.dailyexpense.savingsgoal");
    }

    @Test
    void controllersDoNotAccessRepositories() {
        noClasses()
            .that().resideInAPackage("..controller..")
            .should().accessClassesThat().resideInAPackage("..repository..")
            .allowEmptyShould(true)
            .check(classes);
    }

    @Test
    void controllersDoNotExposeEntities() {
        noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .should().haveRawReturnType(
                com.tngtech.archunit.base.DescribedPredicate.describe("JPA @Entity",
                    t -> t.isAnnotatedWith(jakarta.persistence.Entity.class)))
            .allowEmptyShould(true)
            .check(classes);
    }

    @Test
    void noImportsFromOtherBoundedContexts() {
        noClasses()
            .that().resideInAPackage("com.dailyexpense.savingsgoal..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.dailyexpense.user..",
                "com.dailyexpense.category..",
                "com.dailyexpense.expense..",
                "com.dailyexpense.budget..")
            .allowEmptyShould(true)
            .check(classes);
    }

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
}
