import { useCallback } from "react";
import { useParams } from "react-router-dom";
import { useAppSelector } from "../../../store/hooks";
import { getSectionDetail } from "../api/academicHierarchyApi";
import { DetailFrame } from "./DetailPageFrame";

export default function SectionDetailPage() {
  const { institutionId, sectionId } = useParams<{ institutionId: string; sectionId: string }>();
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);

  const load = useCallback(() => {
    if (!tenantId) return Promise.reject(new Error("Tenant context is unavailable"));
    if (!institutionId) return Promise.reject(new Error("Missing institution identifier"));
    if (!sectionId) return Promise.reject(new Error("Missing section identifier"));
    return getSectionDetail(tenantId, institutionId, sectionId);
  }, [tenantId, institutionId, sectionId]);

  return (
    <DetailFrame
      title="Section details"
      backTo="/university/setup/structure"
      backLabel="Academic structure"
      load={load}
      fields={[
        "sectionId", "institutionId", "academicSessionId", "semesterId", "classId",
        "sectionName", "capacity", "classTeacherUserId", "status",
      ]}
    />
  );
}
