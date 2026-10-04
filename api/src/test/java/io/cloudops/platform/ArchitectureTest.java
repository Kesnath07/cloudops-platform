package io.cloudops.platform;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Enforces the modular-monolith boundaries. Modules talk to each other only through their
 * {@code application} services and published {@code events}; persistence and web adapters are
 * private to their module.
 */
@AnalyzeClasses(packages = "io.cloudops.platform", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "io.cloudops.platform.";
    private static final List<String> MODULES =
            List.of("identity", "catalog", "deployments", "incidents", "notifications", "overview");

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles = slices()
            .matching("io.cloudops.platform.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().resideInAPackage("..persistence..");

    @ArchTest
    static final ArchRule controllersLiveInWebPackages = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAnyPackage("..api..", "..shared.web..", "..overview..");

    @ArchTest
    static final ArchRule sharedKernelDependsOnNoModule = noClasses()
            .that().resideInAPackage("..shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    MODULES.stream().map(module -> ROOT + module + "..").toArray(String[]::new));

    @ArchTest
    static void persistenceAndEntitiesArePrivateToTheirModule(JavaClasses classes) {
        for (String module : MODULES) {
            String own = ROOT + module + "..";
            noClasses().that().resideOutsideOfPackage(own)
                    .should().dependOnClassesThat().resideInAnyPackage(ROOT + module + ".persistence..",
                            ROOT + module + ".api..")
                    .allowEmptyShould(true)
                    .check(classes);
            noClasses().that().resideOutsideOfPackage(own)
                    .should().dependOnClassesThat(
                            JavaClass.Predicates.resideInAPackage(own)
                                    .and(CanBeAnnotated.Predicates
                                            .annotatedWith(Entity.class)))
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }
}
