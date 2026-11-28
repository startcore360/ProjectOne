package com.edu360.api.platformops.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.edu360.api.platformops.entitlement.EntitlementCatalog.Module;

/**
 * EntitlementCatalog.sortByDependencyDepth: a module that requires nothing (e.g. USERS_ACCESS)
 * must always sort above anything that needs it enabled first, inside its own phase - found by
 * actually opening the tenant features editor against a plan with every entitlement off and
 * seeing every visible checkbox disabled, because USERS_ACCESS (the only one that was actually
 * togglable) sorted alphabetically below everything that required it.
 */
class EntitlementCatalogTest {

    private static Module module(String code, String name, int phase, String... requires) {
        return new Module(code, name, phase, List.of(), List.of(requires));
    }

    @Test
    void foundationalModuleSortsBeforeWhatRequiresIt_evenWhenAlphabeticallyLast() {
        List<Module> modules = List.of(
                module("ASSIGNMENTS", "Assignments, homework and study material", 1, "INSTITUTION_SETUP", "USERS_ACCESS"),
                module("ATTENDANCE", "Attendance", 1, "TIMETABLE", "USERS_ACCESS"),
                module("INSTITUTION_SETUP", "Institution setup and academic structure", 1, "USERS_ACCESS"),
                module("TIMETABLE", "Timetable and online classes", 1, "INSTITUTION_SETUP", "USERS_ACCESS"),
                module("USERS_ACCESS", "Users, roles and access", 1));
        Map<String, List<String>> requires = Map.of(
                "ASSIGNMENTS", List.of("INSTITUTION_SETUP", "USERS_ACCESS"),
                "ATTENDANCE", List.of("TIMETABLE", "USERS_ACCESS"),
                "INSTITUTION_SETUP", List.of("USERS_ACCESS"),
                "TIMETABLE", List.of("INSTITUTION_SETUP", "USERS_ACCESS"),
                "USERS_ACCESS", List.of());

        List<String> order = EntitlementCatalog.sortByDependencyDepth(modules, requires)
                .stream().map(Module::moduleCode).toList();

        assertThat(order).containsExactly("USERS_ACCESS", "INSTITUTION_SETUP", "ASSIGNMENTS", "TIMETABLE", "ATTENDANCE");
    }

    @Test
    void modulesWithEqualDepthFallBackToAlphabeticalOrder() {
        List<Module> modules = List.of(
                module("COMMUNITY", "Community feed and events", 1, "USERS_ACCESS"),
                module("COMMUNICATION", "Communication", 1, "USERS_ACCESS"),
                module("USERS_ACCESS", "Users, roles and access", 1));
        Map<String, List<String>> requires = Map.of(
                "COMMUNITY", List.of("USERS_ACCESS"),
                "COMMUNICATION", List.of("USERS_ACCESS"),
                "USERS_ACCESS", List.of());

        List<String> order = EntitlementCatalog.sortByDependencyDepth(modules, requires)
                .stream().map(Module::moduleCode).toList();

        assertThat(order).containsExactly("USERS_ACCESS", "COMMUNICATION", "COMMUNITY");
    }

    @Test
    void laterPhaseModuleNeverSortsAboveAnEarlierPhaseOne() {
        List<Module> modules = List.of(
                module("ADMISSIONS", "Admissions", 2),
                module("USERS_ACCESS", "Users, roles and access", 1));

        List<String> order = EntitlementCatalog.sortByDependencyDepth(modules, Map.of())
                .stream().map(Module::moduleCode).toList();

        assertThat(order).containsExactly("USERS_ACCESS", "ADMISSIONS");
    }

    @Test
    void aDependencyCycleInReferenceDataDoesNotHangTheSort() {
        List<Module> modules = List.of(module("A", "A", 1, "B"), module("B", "B", 1, "A"));
        Map<String, List<String>> requires = Map.of("A", List.of("B"), "B", List.of("A"));

        List<String> order = EntitlementCatalog.sortByDependencyDepth(modules, requires)
                .stream().map(Module::moduleCode).toList();

        assertThat(order).containsExactlyInAnyOrder("A", "B");
    }
}
