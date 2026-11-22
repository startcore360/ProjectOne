import React,{useCallback} from "react";
import {useParams} from "react-router-dom";
import {getDepartmentDetail} from "../api/academicHierarchyApi";
import {DetailFrame} from "./DetailPageFrame";
export default function DepartmentDetailPage({tenantId}:{tenantId:string}){const p=useParams();const id=p.departmentId;const load=useCallback(()=>id?getDepartmentDetail(tenantId,id):Promise.reject(new Error("Missing record id")),[tenantId,id]);return <DetailFrame title="Department details" backTo="/departments" backLabel="Departments" load={load} fields={["departmentCode","departmentName","institutionId","hodStaffAssignmentId","status","settingsJson"]}/>;}
