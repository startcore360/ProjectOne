package com.edu360.api.rbac;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Read-only on purpose: extends the bare marker interface {@link Repository}, not
 * JpaRepository/CrudRepository, and declares only finder methods - there is no save/delete
 * here at all, because ref.standard_role is seeded exclusively by sql/05_ref_data.sql and this
 * application must never write to it (StandardRole's javadoc).
 */
public interface StandardRoleRepository extends Repository<StandardRole, String> {

    Optional<StandardRole> findById(String roleCode);

    List<StandardRole> findAll();
}
