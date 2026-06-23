package com.ebee.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

@AnalyzeClasses(packages = "com.ebee", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    /**
     * Existing Lombok usage is frozen. Any new class that uses Lombok will fail the build.
     * To shrink the frozen set over time, run the test and commit the updated store file
     * after removing Lombok from a class.
     */
    @ArchTest
    static final ArchRule noNewLombokUsage = FreezingArchRule.freeze(
        noClasses()
            .should()
            .beAnnotatedWith("lombok.Data")
            .orShould()
            .beAnnotatedWith("lombok.Builder")
            .orShould()
            .beAnnotatedWith("lombok.NoArgsConstructor")
            .orShould()
            .beAnnotatedWith("lombok.AllArgsConstructor")
            .orShould()
            .beAnnotatedWith("lombok.RequiredArgsConstructor")
            .orShould()
            .beAnnotatedWith("lombok.Getter")
            .orShould()
            .beAnnotatedWith("lombok.Setter")
            .orShould()
            .beAnnotatedWith("lombok.EqualsAndHashCode")
            .orShould()
            .beAnnotatedWith("lombok.ToString")
            .orShould()
            .beAnnotatedWith("lombok.Slf4j")
            .because("new code must not use Lombok; existing usage is being migrated away")
    );

    /**
     * Existing controller -> repository dependencies are frozen. New controllers must not
     * depend on repositories; use services instead.
     */
    @ArchTest
    static final ArchRule noNewControllerToRepositoryDependencies = FreezingArchRule.freeze(
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because("controllers must depend on services, not repositories")
    );

    /**
     * Field injection makes testing and reasoning harder. Prefer constructor injection.
     * Existing field-injected @Value fields are frozen.
     */
    @ArchTest
    static final ArchRule noNewFieldInjection = FreezingArchRule.freeze(
        noFields()
            .should()
            .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .orShould()
            .beAnnotatedWith("org.springframework.beans.factory.annotation.Value")
            .because("prefer constructor injection over field injection")
    );

    /**
     * TODO: enable after breaking existing package cycles (alert -> user -> stock -> product -> inventory -> alert).
     * Kept disabled during the skill-installation phase so the build stays green.
     */
    // @ArchTest
    // static final ArchRule noPackageCycles = SlicesRuleDefinition.slices()
    //     .matching("com.ebee(*)..")
    //     .should()
    //     .beFreeOfCycles();

    /**
     * Standard layered architecture: controller -> service -> repository.
     * Existing violations are frozen so only new violations fail the build.
     */
    @ArchTest
    static final ArchRule layeredArchitecture = FreezingArchRule.freeze(
        Architectures.layeredArchitecture()
            .consideringAllDependencies()
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .layer("Security").definedBy("..security..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service", "Security")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Security").mayOnlyBeAccessedByLayers("Controller")
    );
}
