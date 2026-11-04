package com.edu360.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the institution-scope addition to TenantContext (OD-03). These are pure
 * ThreadLocal-level checks - no database, no Spring context - proving the fail-closed defaults
 * and null-safety the class comment promises, before TenantRoutingDataSourceInstitutionScopeIT
 * proves the same defaults actually hold Postgres RLS closed.
 */
class TenantContextTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void threeArgSetDefaultsInstitutionIdsToEmptyFailClosedSet() {
        TenantContext.set(UUID.randomUUID(), IsolationMode.SHARED, null);

        assertThat(TenantContext.currentInstitutionIds())
                .as("the 3-arg overload (used by every current production caller) must default "
                        + "to the fail-closed empty set, never 'no restriction'")
                .isEmpty();
    }

    @Test
    void fourArgSetCarriesTheGivenInstitutionIdsThrough() {
        UUID institutionA = UUID.randomUUID();
        UUID institutionB = UUID.randomUUID();
        TenantContext.set(UUID.randomUUID(), IsolationMode.SHARED, null, Set.of(institutionA, institutionB));

        assertThat(TenantContext.currentInstitutionIds()).containsExactlyInAnyOrder(institutionA, institutionB);
    }

    @Test
    void nullInstitutionIdsIsTreatedAsFailClosedEmptySetNotNpe() {
        TenantContext.set(UUID.randomUUID(), IsolationMode.SHARED, null, null);

        assertThat(TenantContext.currentInstitutionIds()).isEmpty();
    }

    @Test
    void institutionIdsIsDefensivelyCopiedAndImmutable() {
        UUID institutionA = UUID.randomUUID();
        var mutableSource = new java.util.HashSet<>(Set.of(institutionA));
        TenantContext.set(UUID.randomUUID(), IsolationMode.SHARED, null, mutableSource);

        mutableSource.add(UUID.randomUUID());

        assertThat(TenantContext.currentInstitutionIds())
                .as("mutating the caller's set afterwards must not change what TenantContext carries")
                .containsExactly(institutionA);
        assertThat(TenantContext.currentInstitutionIds()).isUnmodifiable();
    }

    @Test
    void clearResetsInstitutionIdsBackToTheFailClosedEmptySetToo() {
        TenantContext.set(UUID.randomUUID(), IsolationMode.SHARED, null, Set.of(UUID.randomUUID()));
        assertThat(TenantContext.currentInstitutionIds()).isNotEmpty();

        TenantContext.clear();

        assertThat(TenantContext.isSet()).isFalse();
        assertThat(TenantContext.currentInstitutionIds())
                .as("no context at all must read the same fail-closed empty set as an explicit empty set")
                .isEmpty();
    }

    @Test
    void noContextSetAtAllReadsAsEmptyInstitutionIdsWithoutCallingSet() {
        assertThat(TenantContext.isSet()).isFalse();
        assertThat(TenantContext.currentInstitutionIds()).isEmpty();
    }
}
