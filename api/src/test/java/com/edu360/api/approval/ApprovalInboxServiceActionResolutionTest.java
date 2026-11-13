package com.edu360.api.approval;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
class ApprovalInboxServiceActionResolutionTest {
 @Test void attendanceCorrectionUsesCorrectionApprovalAction(){
  ApprovalRequest request=mock(ApprovalRequest.class);
  when(request.getSubjectType()).thenReturn("ATTENDANCE_CORRECTION_REQUEST");
  when(request.getProcessCode()).thenReturn("ATTENDANCE_CORRECTION_COLLEGE");
  assertThat(ApprovalInboxService.resolveApprovalAction(request)).isEqualTo("ATTENDANCE_CORRECTION_APPROVE");
 }
 @Test void attendanceBulkUploadUsesBulkUploadApprovalAction(){
  ApprovalRequest request=mock(ApprovalRequest.class);
  when(request.getSubjectType()).thenReturn("ATTENDANCE_BULK_UPLOAD");
  when(request.getProcessCode()).thenReturn("ATTENDANCE_BULK_UPLOAD_ADMIN");
  assertThat(ApprovalInboxService.resolveApprovalAction(request)).isEqualTo("ATTENDANCE_BULK_UPLOAD_APPROVE");
 }
 @Test void unknownProcessFailsClosed(){
  ApprovalRequest request=mock(ApprovalRequest.class);
  when(request.getSubjectType()).thenReturn("UNKNOWN");
  when(request.getProcessCode()).thenReturn("UNKNOWN_PROCESS");
  assertThat(ApprovalInboxService.resolveApprovalAction(request)).isNull();
 }
}
