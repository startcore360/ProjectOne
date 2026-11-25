import api from "../../../services/axios";

export type Notice = { notice_id:string; title:string; body:string; is_urgent:boolean; requires_acknowledgment:boolean; published_at:string; acknowledged:boolean };
export type CatalogBook = { id:string; isbn:string|null; title:string; author:string|null; category:string|null; total_copies:number; available_copies:number };
export const t10Api = {
  notices: async (tenant:string,institution:string) => (await api.get<Notice[]>(`/tenants/${tenant}/notice-bulletins`,{params:{institutionId:institution}})).data,
  publishNotice: async (tenant:string,body:unknown) => (await api.post(`/tenants/${tenant}/notice-bulletins`,body)).data,
  acknowledge: async (tenant:string,institution:string,id:string) => api.post(`/tenants/${tenant}/notice-bulletins/${id}/acknowledgment`,null,{params:{institutionId:institution}}),
  catalog: async (tenant:string,institution:string,q="") => (await api.get<CatalogBook[]>(`/tenants/${tenant}/library/catalog`,{params:{institutionId:institution,q}})).data,
  dispatch: async (tenant:string,body:unknown) => (await api.post(`/tenants/${tenant}/communications/dispatch`,body)).data,
  routes: async (tenant:string,institution:string) => (await api.get<Record<string,unknown>[]>(`/tenants/${tenant}/transport/routes`,{params:{institutionId:institution}})).data,
  dashboard: async (tenant:string,institution:string) => (await api.get<Record<string,unknown>>(`/tenants/${tenant}/institutional-reports/executive-dashboard`,{params:{institutionId:institution}})).data,
};
