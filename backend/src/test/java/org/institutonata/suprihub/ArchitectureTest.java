package org.institutonata.suprihub;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Regras de arquitetura verificadas automaticamente. */
@AnalyzeClasses(packages = "org.institutonata.suprihub", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Regras de domínio são puras: sem Spring (testáveis sem contexto). */
    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .as("Classes de domínio não dependem do Spring");

    /** O motor de julgamento (Fase 2) não acessa banco nem JPA. */
    @ArchTest
    static final ArchRule judgmentEngineIsPure = noClasses()
            .that().resideInAPackage("..judgment.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..",
                    "org.springframework..", "..infra..")
            .allowEmptyShould(true)
            .as("Motor de julgamento é componente puro");

    /** Controllers não usam repositórios diretamente: passam pelos services. */
    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .as("Controllers não acessam repositórios");

    /** O núcleo compartilhado não depende dos módulos de negócio. */
    @ArchTest
    static final ArchRule sharedIsIndependent = noClasses()
            .that().resideInAPackage("..suprihub.shared..")
            .should().dependOnClassesThat().resideInAnyPackage("..suprihub.auth..", "..suprihub.supplier..",
                    "..suprihub.tender..", "..suprihub.proposal..", "..suprihub.judgment..",
                    "..suprihub.catalog..", "..suprihub.notification..")
            .as("shared não depende de módulos");
}
