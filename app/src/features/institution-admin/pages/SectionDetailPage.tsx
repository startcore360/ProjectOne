import {useCallback} from "react";
import {useParams} from "react-router-dom";
import {useAppSelector} from "../../../store/hooks";
import {getSectionDetail} from "../api/academicHierarchyApi";
import {DetailFrame} from "./DetailPageFrame";
export default function SectionDetailPage(){
 const params=useParams();
 const tenantId=useAppSelector(state=>state.auth.user?.tenantId);
 const institutionId=params.institutionId;
 const id=params.sectionId;
 const load=useCallback(()=>{
  if(!tenantId) return Promise.reject(new Error("Tenant context is unavailable"));
  if(!id) return Promise.reject(new Error("Missing record identifier"));
  if(!institutionId) return Promise.reject(new Error('Missing institution identifier'));
  return getSectionDetail(tenantId,institutionId!,id);
 },[tenantId,institutionId,id]);
 return <DetailFrame title="Section details" backTo="/university/setup/structure" backLabel="Academic structure" load={load} fields={["sectionId","institutionId","academicSessionId","semesterId","classId","sectionName","capacity","classTeacherUserId","status"]}/>;
}
