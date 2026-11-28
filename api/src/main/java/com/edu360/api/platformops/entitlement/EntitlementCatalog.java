package com.edu360.api.platformops.entitlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * E2-S2 (PD-11): the 29 modules (ref.module_catalog), their features (ref.feature_catalog, marked
 * provisional until OD-43) and the hard "requires" links between modules (ref.module_dependency,
 * kind REQUIRED). Read-only reference data, so the main pool is used; RLS does not apply to ref.
 */
@Service
public class EntitlementCatalog {

    public record Feature(String featureCode, String description, boolean provisional) {
    }

    public record Module(String moduleCode, String moduleName, int phase, List<Feature> features,
                         List<String> requires) {
    }

    private final JdbcTemplate jdbc;

    public EntitlementCatalog(@Qualifier("dataSource") DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public List<Module> modules() {
        Map<String, List<Feature>> features = new LinkedHashMap<>();
        jdbc.query("SELECT feature_code, module_code, description, COALESCE(provisional, false) AS provisional "
                        + "FROM ref.feature_catalog ORDER BY module_code, feature_code",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> features
                        .computeIfAbsent(rs.getString("module_code"), k -> new ArrayList<>())
                        .add(new Feature(rs.getString("feature_code"), rs.getString("description"), rs.getBoolean("provisional"))));
        Map<String, List<String>> requires = new LinkedHashMap<>();
        jdbc.query("SELECT module_code, requires_module_code FROM ref.module_dependency WHERE kind = 'REQUIRED' "
                        + "ORDER BY module_code, requires_module_code",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> requires
                        .computeIfAbsent(rs.getString("module_code"), k -> new ArrayList<>())
                        .add(rs.getString("requires_module_code")));
        List<Module> modules = jdbc.query("SELECT module_code, module_name, phase FROM ref.module_catalog WHERE is_entitlement "
                        + "ORDER BY phase, module_name",
                (rs, i) -> new Module(rs.getString("module_code"), rs.getString("module_name"), rs.getInt("phase"),
                        List.copyOf(features.getOrDefault(rs.getString("module_code"), List.of())),
                        List.copyOf(requires.getOrDefault(rs.getString("module_code"), List.of()))));

        return sortByDependencyDepth(modules, requires);
    }

    /**
     * Plain alphabetical order buries a foundational module like USERS_ACCESS - which nothing
     * requires but everything else requires - below every module that needs it enabled first:
     * found by actually opening the tenant features editor with an empty plan and seeing every
     * checkbox disabled with no visibly-enabled one above the fold to unblock them. Sorting by
     * dependency depth first keeps a module that requires nothing (depth 0) always above anything
     * that (transitively) requires it. Package-visible (not private) so EntitlementCatalogTest can
     * verify the ordering directly, without a database.
     */
    static List<Module> sortByDependencyDepth(List<Module> modules, Map<String, List<String>> requires) {
        Map<String, Integer> depth = new HashMap<>();
        modules.forEach(m -> dependencyDepth(m.moduleCode(), requires, depth, new java.util.HashSet<>()));
        List<Module> sorted = new ArrayList<>(modules);
        sorted.sort(Comparator.comparingInt(Module::phase)
                .thenComparingInt(m -> depth.getOrDefault(m.moduleCode(), 0))
                .thenComparing(Module::moduleName));
        return sorted;
    }

    /** 0 for a module nothing requires; otherwise 1 + the deepest of what it itself requires.
     *  visiting guards against a cycle in the reference data turning this into infinite recursion -
     *  module_dependency is meant to be a DAG, but this must never hang the whole screen if it isn't. */
    private static int dependencyDepth(String moduleCode, Map<String, List<String>> requires,
                                        Map<String, Integer> memo, Set<String> visiting) {
        Integer cached = memo.get(moduleCode);
        if (cached != null) return cached;
        List<String> deps = requires.get(moduleCode);
        if (deps == null || deps.isEmpty() || !visiting.add(moduleCode)) {
            memo.put(moduleCode, 0);
            return 0;
        }
        int depth = 1 + deps.stream().mapToInt(dep -> dependencyDepth(dep, requires, memo, visiting)).max().orElse(0);
        visiting.remove(moduleCode);
        memo.put(moduleCode, depth);
        return depth;
    }

    /** module -> the modules it requires. */
    public static Map<String, Set<String>> requiresMap(List<Module> modules) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        modules.forEach(m -> map.put(m.moduleCode(), Set.copyOf(m.requires())));
        return map;
    }
}
