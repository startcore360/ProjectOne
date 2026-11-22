import React,{useCallback} from "react";
import {useParams} from "react-router-dom";
import {getSectionDetail} from "../api/academicHierarchyApi";
import {DetailFrame} from "./DetailPageFrame";
export default function SectionDetailPage({tenantId}:{tenantId:string}){const p=useParams();const id=p.sectionId;const load=useCallback(()=>id?getSectionDetail(tenantId,id):Promise.reject(new Error("Missing record id")),[tenantId,id]);return <DetailFrame title="Section details" backTo="/sections" backLabel="Sections" load={load} fields={["sectionName","institutionId","academicSessionId","semesterId","classId","capacity","classTeacherUserId","status"]}/>;}
