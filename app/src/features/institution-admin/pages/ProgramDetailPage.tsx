import {useCallback} from "react";
import {useParams} from "react-router-dom";
import {useAppSelector} from "../../../store/hooks";
import {getProgramDetail} from "../api/academicHierarchyApi";
import {DetailFrame} from "./DetailPageFrame";
export default function ProgramDetailPage(){
 const params=useParams();
 const tenantId=useAppSelector(state=>state.auth.user?.tenantId);
 const institutionId=params.institutionId;
 const id=params.programId;
 const load=useCallback(()=>{
  if(!tenantId) return Promise.reject(new Error("Tenant context is unavailable"));
  if(!id) return Promise.reject(new Error("Missing record identifier"));
  if(!institutionId) return Promise.reject(new Error('Missing institution identifier'));
  return getProgramDetail(tenantId,institutionId!,id);
 },[tenantId,institutionId,id]);
 return <DetailFrame title="Program details" backTo="/university/setup/structure" backLabel="Academic structure" load={load} fields={["programId","institutionId","departmentId","programCode","programName","degreeLevel","durationYears","semestersPerYear","totalCredits","curriculumVersion","status"]}/>;
}
