package com.edu360.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StudentLoginLookupTest {
    private EntityManager entityManager;
    private Query query;
    private StudentLoginLookup lookup;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        lookup = new StudentLoginLookup();
        ReflectionTestUtils.setField(lookup, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("email"), anyString())).thenReturn(query);
    }

    @Test
    void resolvesOnlyVerifiedSecondaryEmailAliases() {
        UUID accountId = UUID.randomUUID();
        when(query.getResultList()).thenReturn(List.of(accountId));

        assertThat(lookup.accountForVerifiedEmailAlias(" Personal@Example.edu ")).contains(accountId);
        verify(entityManager).createNativeQuery(contains("is_verified = true"));
        verify(query).setParameter("email", " Personal@Example.edu ");
    }

    @Test
    void doesNotResolveAnUnverifiedAlias() {
        when(query.getResultList()).thenReturn(List.of());

        assertThat(lookup.accountForVerifiedEmailAlias("personal@example.edu")).isEmpty();
    }

    @Test
    void blankEmailDoesNotQueryTheDatabase() {
        assertThat(lookup.accountForVerifiedEmailAlias("  ")).isEmpty();
        verify(entityManager, never()).createNativeQuery(anyString());
    }
}
