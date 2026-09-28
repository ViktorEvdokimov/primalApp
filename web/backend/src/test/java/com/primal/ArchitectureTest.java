package com.primal;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/** Границы модулей бэкенда ({@code doc/architecture.md} §3). */
@AnalyzeClasses(packages = "com.primal", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final List<String> MODULES = List.of(
            "common", "identity", "access", "mail", "realtime", "catalog", "rules", "campaign", "progression");

    /** Правила наград и условий — чистая Java: только JDK и сам модуль {@code rules}. */
    @ArchTest
    static final ArchRule rulesDependOnlyOnJdk = noClasses()
            .that().resideInAPackage("com.primal.rules..")
            .should().dependOnClassesThat(not(resideInAPackage("com.primal.rules..")).and(not(resideInAPackage("java.."))))
            .allowEmptyShould(true)
            .because("модуль rules — чистая Java без Spring, JPA и других модулей");

    /** Контроллеры тонкие: работают через сервисы, а не через репозитории. */
    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat(assignableTo(Repository.class))
            .allowEmptyShould(true)
            .because("контроллеры обращаются к данным только через сервисы");

    /** Модуль не обращается к репозиториям другого модуля — только к его сервисам. */
    @ArchTest
    static void repositoriesAreUsedOnlyInsideTheirModule(JavaClasses classes) {
        for (String module : MODULES) {
            String modulePackage = "com.primal." + module + "..";
            noClasses()
                    .that().resideOutsideOfPackage(modulePackage)
                    .should().dependOnClassesThat(resideInAPackage(modulePackage).and(assignableTo(Repository.class)))
                    .allowEmptyShould(true)
                    .because("репозитории модуля " + module + " используются только внутри него")
                    .check(classes);
        }
    }
}
