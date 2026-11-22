import React,{useCallback} from "react";
import {useParams} from "react-router-dom";
import {getInstitutionDetail} from "../api/academicHierarchyApi";
import {DetailFrame} from "./DetailPageFrame";
export default function InstitutionDetailPage({tenantId}:{tenantId:string}){const p=useParams();const id=p.institutionId;const load=useCallback(()=>id?getInstitutionDetail(tenantId,id):Promise.reject(new Error("Missing record id")),[tenantId,id]);return <DetailFrame title="Institution details" backTo="/institutions" backLabel="Institutions" load={load} fields={["institutionCode","institutionName","institutionType","status","underUniversity","externalUniversityMode","settingsJson"]}/>;}
