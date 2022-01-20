package com.example.thandbag.architecture;

import com.example.thandbag.ThandbagApplication;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packagesOf = ThandbagApplication.class)
public class PackageTest {

    // 1. controller 패키지의 클래스는 repository 패키지의 클래스를 직접 호출할 수 없다.
    @ArchTest
    ArchRule controllerPackageRule = noClasses().that().resideInAPackage("..controller..")
            .and().areAnnotatedWith("Test")
            .should().accessClassesThat().resideInAPackage("..repository..");

    // 2. repository 패키지의 클래스는 service와 repository 패키지에서만 호출할 수 있다.
    @ArchTest
    ArchRule repositoryPackageRule = classes().that().resideInAPackage("..repository..")
            .and().areAnnotatedWith("Test")
            .should().onlyBeAccessed().byClassesThat().resideInAnyPackage("..service..", "..repository..");

    // 3. repository 패키지에서는 service와 controller를 호출할 수 없다.
    @ArchTest
    ArchRule repositoryPackageRule2 = noClasses().that().resideInAPackage("..repository..")
            .and().areAnnotatedWith("Test")
            .should().accessClassesThat().resideInAnyPackage("..service..", "..controller..");

    // 4. 이름이 controller로 끝나는 클래스는 controller 패키지 내에 있어야 한다.
    @ArchTest
    ArchRule controllerClassRule = classes().that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..controller..");

    // 5. 이름이 service로 끝나는 클래스는 service 패키지 내에 있어야 한다.
    @ArchTest
    ArchRule serviceClassRule = classes().that().haveSimpleNameEndingWith("Service")
            .should().resideInAPackage("..service..");

    // 6. 이름이 repository로 끝나는 클래스는 repository 패키지 내에 있어야 한다.
    @ArchTest
    ArchRule repositoryClassRule = classes().that().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage("..repository..");

    // 7. 이름이 dto로 끝나는 클래스는 dto 패키지 내에 있어야 한다.
    @ArchTest
    ArchRule dtoClassRule = classes().that().haveSimpleNameEndingWith("Dto")
            .should().resideInAPackage("..dto..");

    // 8. enum 타입은 enum 패키지 내에만 있어야 한다.
    @ArchTest
    ArchRule enumClassRule = classes().that().areEnums()
            .should().resideInAPackage("..Enum..");

    // 9. 순환참조가 없어야 한다.
    @ArchTest
    ArchRule freeOfCycles = slices().matching("..thandbag.(*)..")
            .should().beFreeOfCycles();
}
