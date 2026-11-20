package com.edu360.api.imports;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.StudentCreateRequest;
import com.edu360.api.identity.StudentCreationService;
import com.edu360.api.identity.StudentProfileResponse;
import com.edu360.api.identity.UserAccountService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.dto.CreateUserRequest;

import lombok.RequiredArgsConstructor;

/**
 * Commits each validated CSV row independently so GOOD_ROWS_ONLY can preserve successful rows
 * when a later row fails. Student rows use the normal student profile/enrolment lifecycle.
 */
@Service
@RequiredArgsConstructor
public class UserBulkImportRowService {
    private final UserAccountService userAccounts;
    private final StudentCreationService students;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUser(RbacPrincipal actor, CreateUserRequest request) {
        userAccounts.createUser(actor, request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentProfileResponse createStudent(TenantPrincipal actor, StudentCreateRequest request) {
        return students.create(actor, request);
    }
}
