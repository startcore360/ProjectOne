import React,{useCallback} from "react";
import {useParams} from "react-router-dom";
import {getAcademicSessionDetail} from "../api/academicHierarchyApi";
import {DetailFrame} from "./DetailPageFrame";
export default function AcademicSessionDetailPage({tenantId}:{tenantId:string}){const p=useParams();const id=p.academicSessionId;const load=useCallback(()=>id?getAcademicSessionDetail(tenantId,id):Promise.reject(new Error("Missing record id")),[tenantId,id]);return <DetailFrame title="Academic session details" backTo="/academic-calendar" backLabel="Academic calendar" load={load} fields={["sessionLabel","academicYear","startDate","endDate","status","isCurrent","institutionId"]}/>;}
