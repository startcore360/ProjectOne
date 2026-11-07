package com.edu360.api.rbac;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Read-only on purpose (see StandardRoleRepository's javadoc for why): ref.action_catalog is
 * seeded exclusively by sql/05_ref_data.sql.
 */
public interface ActionCatalogRepository extends Repository<ActionCatalog, String> {

    Optional<ActionCatalog> findById(String actionCode);

    List<ActionCatalog> findAllById(Iterable<String> actionCodes);

    List<ActionCatalog> findAll();
}
