import React,{useCallback} from "react";
import {useParams} from "react-router-dom";
import {getProgramDetail} from "../api/academicHierarchyApi";
import {DetailFrame} from "./DetailPageFrame";
export default function ProgramDetailPage({tenantId}:{tenantId:string}){const p=useParams();const id=p.programId;const load=useCallback(()=>id?getProgramDetail(tenantId,id):Promise.reject(new Error("Missing record id")),[tenantId,id]);return <DetailFrame title="Program details" backTo="/programs" backLabel="Programs" load={load} fields={["programCode","programName","departmentId","degreeLevel","durationYears","semestersPerYear","totalCredits","curriculumVersion","status"]}/>;}
