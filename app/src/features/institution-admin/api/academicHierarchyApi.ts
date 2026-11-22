export type AcademicDetail = Record<string, unknown>;
async function call<T>(url:string,init?:RequestInit):Promise<T>{
 const response=await fetch(url,{credentials:"same-origin",headers:{Accept:"application/json",...(init?.body?{"Content-Type":"application/json"}:{}),...init?.headers},...init});
 if(!response.ok){const payload=await response.json().catch(()=>null);throw new Error(payload?.message||payload?.detail||response.statusText)}
 if(response.status===204)return undefined as T;
 return response.json() as Promise<T>;
}
const tenantBase=(tenantId:string)=>{if(!tenantId?.trim())throw new Error("Tenant context required");return `/api/tenants/${encodeURIComponent(tenantId)}`};
const institutionBase=(tenantId:string,institutionId:string)=>{if(!institutionId?.trim())throw new Error("Institution context required");return `${tenantBase(tenantId)}/institutions/${encodeURIComponent(institutionId)}`};
export const getInstitutionDetail=(tenantId:string,institutionId:string)=>call<AcademicDetail>(`${tenantBase(tenantId)}/institutions/${encodeURIComponent(institutionId)}`);
export const getDepartmentDetail=(tenantId:string,institutionId:string,id:string)=>call<AcademicDetail>(`${institutionBase(tenantId,institutionId)}/departments/${encodeURIComponent(id)}`);
export const getProgramDetail=(tenantId:string,institutionId:string,id:string)=>call<AcademicDetail>(`${institutionBase(tenantId,institutionId)}/programs/${encodeURIComponent(id)}`);
export const getSectionDetail=(tenantId:string,institutionId:string,id:string)=>call<AcademicDetail>(`${institutionBase(tenantId,institutionId)}/sections/${encodeURIComponent(id)}`);
export const getAcademicSessionDetail=(tenantId:string,institutionId:string,id:string)=>call<AcademicDetail>(`${institutionBase(tenantId,institutionId)}/academic-sessions/${encodeURIComponent(id)}`);
