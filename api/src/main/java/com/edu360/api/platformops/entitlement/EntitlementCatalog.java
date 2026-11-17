package com.edu360.api.platformops.entitlement;

import java.util.ArrayList;
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
        return jdbc.query("SELECT module_code, module_name, phase FROM ref.module_catalog WHERE is_entitlement "
                        + "ORDER BY phase, module_name",
                (rs, i) -> new Module(rs.getString("module_code"), rs.getString("module_name"), rs.getInt("phase"),
                        List.copyOf(features.getOrDefault(rs.getString("module_code"), List.of())),
                        List.copyOf(requires.getOrDefault(rs.getString("module_code"), List.of()))));
    }

    /** module -> the modules it requires. */
    public static Map<String, Set<String>> requiresMap(List<Module> modules) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        modules.forEach(m -> map.put(m.moduleCode(), Set.copyOf(m.requires())));
        return map;
    }
}
