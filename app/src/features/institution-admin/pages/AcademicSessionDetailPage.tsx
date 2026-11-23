import {useCallback,useState} from "react";
import {useParams} from "react-router-dom";
import {useAppSelector} from "../../../store/hooks";
import {getAcademicSessionDetail,setCurrentAcademicSession} from "../api/academicHierarchyApi";
import {DetailFrame} from "./DetailPageFrame";
export default function AcademicSessionDetailPage(){
 const params=useParams();
 const tenantId=useAppSelector(state=>state.auth.user?.tenantId);
 const institutionId=params.institutionId;
 const id=params.academicSessionId;
 const [revision,setRevision]=useState(0);
 const [saving,setSaving]=useState(false);
 const [feedback,setFeedback]=useState("");
 const load=useCallback(()=>{
  if(!tenantId) return Promise.reject(new Error("Tenant context is unavailable"));
  if(!id) return Promise.reject(new Error("Missing record identifier"));
  if(!institutionId) return Promise.reject(new Error("Missing institution identifier"));
  return getAcademicSessionDetail(tenantId,institutionId,id);
 },[tenantId,institutionId,id,revision]);
 const makeCurrent=async()=>{
  if(!tenantId||!institutionId||!id)return;
  setSaving(true);setFeedback("");
  try{await setCurrentAcademicSession(tenantId,institutionId,id);setFeedback("This academic session is now current.");setRevision(v=>v+1);}
  catch(error){setFeedback(error instanceof Error?error.message:"Unable to set current session");}
  finally{setSaving(false);}
 };
 return <><div style={{display:"flex",gap:12,alignItems:"center",marginBottom:12}}><button type="button" onClick={makeCurrent} disabled={saving}>{saving?"Saving…":"Make current academic session"}</button>{feedback&&<span role="status">{feedback}</span>}</div><DetailFrame title="Academic session details" backTo="/university/setup/structure" backLabel="Academic structure" load={load} fields={["academicSessionId","sessionLabel","academicYear","startDate","endDate","status","current","institutionId"]}/></>;
}
