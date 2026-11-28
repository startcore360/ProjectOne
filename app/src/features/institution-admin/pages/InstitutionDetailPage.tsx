import { useCallback } from "react";
import { useParams } from "react-router-dom";
import { useAppSelector } from "../../../store/hooks";
import { getInstitutionDetail } from "../api/academicHierarchyApi";
import { DetailFrame } from "./DetailPageFrame";

export default function InstitutionDetailPage() {
  const { institutionId } = useParams<{ institutionId: string }>();
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);

  const load = useCallback(() => {
    if (!tenantId) return Promise.reject(new Error("Tenant context is unavailable"));
    if (!institutionId) return Promise.reject(new Error("Missing institution identifier"));
    return getInstitutionDetail(tenantId, institutionId);
  }, [tenantId, institutionId]);

  return (
    <DetailFrame
      title="Institution details"
      backTo="/university/institutions"
      backLabel="Institutions"
      load={load}
      fields={[
        "institutionId",
        "institutionCode",
        "institutionName",
        "institutionType",
        "status",
        "externalUniversityMode",
        "underUniversity",
        "addressLine",
        "city",
        "stateName",
        "postalCode",
        "principalInviteEmail",
        "settingsJson",
      ]}
    />
  );
}
