import {useCallback} from "react";
import {useParams} from "react-router-dom";
import {useAppSelector} from "../../../store/hooks";
import {getInstitutionDetail} from "../api/academicHierarchyApi";
import {DetailFrame} from "./DetailPageFrame";
export default function InstitutionDetailPage(){
 const params=useParams();
 const tenantId=useAppSelector(state=>state.auth.user?.tenantId);
 const institutionId=params.institutionId || params.institutionId;
 const id=params.institutionId;
 const load=useCallback(()=>{
  if(!tenantId) return Promise.reject(new Error("Tenant context is unavailable"));
  if(!id) return Promise.reject(new Error("Missing record identifier"));
  
  return getInstitutionDetail(tenantId,id);
 },[tenantId,institutionId,id]);
 return <DetailFrame title="Institution details" backTo="/university/institutions" backLabel="Institutions" load={load} fields={["institutionId","institutionCode","institutionName","institutionType","status","externalUniversityMode","underUniversity","settingsJson"]}/>;
}
