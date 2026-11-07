package com.edu360.api.approval;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalBatchRepository extends JpaRepository<ApprovalBatch, ApprovalBatchId> {
}
