package com.edu360.api.platformops.entitlement;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;

import com.edu360.api.exception.ApiException;

/**
 * E2-S2: the rules a change to plan or tenant entitlements must keep. Keys are "MODULE" for a
 * module switch and "MODULE:FEATURE" for a feature switch, as stored today. Only the keys the
 * request changes are checked, so older inconsistent rows never block an unrelated save.
 */
public final class EntitlementRules {

    private EntitlementRules() {
    }

    /**
     * @param result   every switch after the change (true = on; a missing key is off)
     * @param changed  the switches the request sets
     * @param requires module -> modules it requires (ref.module_dependency, REQUIRED)
     */
    public static void validate(Map<String, Boolean> result, Map<String, Boolean> changed,
                                Map<String, Set<String>> requires) {
        for (Map.Entry<String, Boolean> change : changed.entrySet()) {
            String key = change.getKey();
            boolean on = Boolean.TRUE.equals(change.getValue());
            int colon = key.indexOf(':');
            if (colon > 0) {
                String module = key.substring(0, colon);
                if (on && !isOn(result, module)) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MODULE_OFF",
                            "MODULE_OFF: turn on the " + module + " module before its feature " + key.substring(colon + 1) + ".");
                }
                continue;
            }
            if (on) {
                Set<String> missing = new TreeSet<>();
                for (String needed : requires.getOrDefault(key, Set.of())) {
                    if (!isOn(result, needed)) missing.add(needed);
                }
                if (!missing.isEmpty()) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MODULE_DEPENDENCY",
                            "MODULE_DEPENDENCY: " + key + " needs " + String.join(", ", missing) + " to be on.");
                }
            } else {
                Set<String> dependants = new TreeSet<>();
                requires.forEach((module, needs) -> {
                    if (needs.contains(key) && isOn(result, module)) dependants.add(module);
                });
                if (!dependants.isEmpty()) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MODULE_DEPENDENCY",
                            "MODULE_DEPENDENCY: " + String.join(", ", dependants) + " still needs " + key
                                    + ". Turn " + (dependants.size() == 1 ? "it" : "them") + " off first.");
                }
            }
        }
    }

    /** Keys a tenant override turns on that its plan does not have on (Product Owner only, WF-06). */
    public static List<String> beyondPlan(Map<String, Boolean> plan, Map<String, Boolean> changed) {
        return changed.entrySet().stream()
                .filter(e -> Boolean.TRUE.equals(e.getValue()) && !isOn(plan, e.getKey()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private static boolean isOn(Map<String, Boolean> state, String key) {
        return Boolean.TRUE.equals(state.get(key));
    }
}
