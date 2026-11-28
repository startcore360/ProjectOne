import { useRoutes, type RouteObject } from "react-router-dom";
import { StudentDirectoryPage, StudentDetailPage, StudentSubrecordDetailPage } from "../features/students/pages/StudentDirectoryPage";

const routes: RouteObject[] = [
  { path: "", element: <StudentDirectoryPage /> },
  { path: ":studentId", element: <StudentDetailPage /> },
  { path: ":studentId/guardians/:recordId", element: <StudentSubrecordDetailPage /> },
  { path: ":studentId/notes/:recordId", element: <StudentSubrecordDetailPage /> },
  { path: ":studentId/legal-holds/:recordId", element: <StudentSubrecordDetailPage /> },
];
export default function StudentFeatureRoutes() { return useRoutes(routes); }