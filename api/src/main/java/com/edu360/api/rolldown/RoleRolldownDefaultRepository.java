package com.edu360.api.rolldown;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRolldownDefaultRepository extends JpaRepository<RoleRolldownDefault, Integer> {
    List<RoleRolldownDefault> findByContextOrderByMissingRoleCodeAscPreferenceAsc(String context);
}
