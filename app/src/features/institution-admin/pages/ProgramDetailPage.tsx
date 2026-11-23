import { useCallback } from "react";
import { useParams } from "react-router-dom";
import { useAppSelector } from "../../../store/hooks";
import { getProgramDetail } from "../api/academicHierarchyApi";
import { DetailFrame } from "./DetailPageFrame";

export default function ProgramDetailPage() {
  const { institutionId, programId } = useParams<{ institutionId: string; programId: string }>();
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);

  const load = useCallback(() => {
    if (!tenantId) return Promise.reject(new Error("Tenant context is unavailable"));
    if (!institutionId) return Promise.reject(new Error("Missing institution identifier"));
    if (!programId) return Promise.reject(new Error("Missing program identifier"));
    return getProgramDetail(tenantId, institutionId, programId);
  }, [tenantId, institutionId, programId]);

  return (
    <DetailFrame
      title="Program details"
      backTo="/university/setup/structure"
      backLabel="Academic structure"
      load={load}
      fields={[
        "programId", "institutionId", "departmentId", "programCode", "programName",
        "degreeLevel", "durationYears", "semestersPerYear", "totalCredits", "curriculumVersion", "status",
      ]}
    />
  );
}
