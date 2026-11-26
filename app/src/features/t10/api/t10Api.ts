import api from "../../../services/axios";

export type Notice = {
  notice_id: string; title: string; body: string; is_urgent: boolean;
  requires_acknowledgment: boolean; published_at: string; acknowledged: boolean;
};
export type CatalogBook = {
  id: string; isbn: string | null; title: string; author: string | null;
  category: string | null; total_copies: number; available_copies: number;
};
export type BookCopy = { id: string; accession_number: string; status: string };
export type Circulation = {
  id: string; book_id: string; title: string; copy_id: string; accession_number: string;
  borrower_user_id: string; borrower_role: string; issued_at: string; due_date: string;
  returned_at: string | null; status: string; current_fine: number;
};
export type LibraryFine = {
  id: string; circulation_id: string; amount: number; amount_paid: number;
  outstanding: number; status: string; assessed_at: string; borrower_user_id: string; title: string;
};
export type LibraryReservation = {
  id: string; book_id: string; title: string; borrower_user_id: string;
  queue_position: number; status: string; created_at: string;
};
export type FinePolicy = {
  grace_period_days: number; calculation_mode: "FLAT_DAILY_AMOUNT";
  daily_amount: number; day_basis: "CALENDAR_DAYS" | "WORKING_DAYS";
  fine_cap: number; working_days: number[] | string; is_placeholder_default: boolean;
};
export type TransportRoute = {
  id: string; route_name: string; vehicle_number: string; driver_name: string;
  driver_phone: string | null; total_capacity: number; allocated_seats: number;
  transport_fee_code: string | null;
};
export type RouteStop = {
  id: string; route_id: string; stop_name: string; pickup_time: string | null;
  drop_time: string | null; fee_amount: number; stop_order: number;
};
export type TransportAllocation = {
  id: string; route_id: string; route_name: string; stop_id: string; stop_name: string;
  passenger_user_id: string; status: string; allocated_at: string;
  fee_amount: number; transport_fee_code: string | null;
};
export const t10Api = {
  notices: async (tenant: string, institution: string) =>
    (await api.get<Notice[]>(`/tenants/${tenant}/notice-bulletins`, { params: { institutionId: institution } })).data,
  publishNotice: async (tenant: string, body: unknown) =>
    (await api.post(`/tenants/${tenant}/notice-bulletins`, body)).data,
  acknowledge: async (tenant: string, institution: string, id: string) =>
    api.post(`/tenants/${tenant}/notice-bulletins/${id}/acknowledgment`, null, { params: { institutionId: institution } }),
  catalog: async (tenant: string, institution: string, q = "") =>
    (await api.get<CatalogBook[]>(`/tenants/${tenant}/library/catalog`, { params: { institutionId: institution, q } })).data,
  addBook: async (tenant: string, body: { institutionId: string; isbn: string; title: string; author: string; category: string; copies: number }) =>
    (await api.post(`/tenants/${tenant}/library/catalog`, body)).data,
  copies: async (tenant: string, institution: string, bookId: string) =>
    (await api.get<BookCopy[]>(`/tenants/${tenant}/library/catalog/${bookId}/copies`, { params: { institutionId: institution } })).data,
  circulations: async (tenant: string, institution: string) =>
    (await api.get<Circulation[]>(`/tenants/${tenant}/library/circulations`, { params: { institutionId: institution } })).data,
  issueBook: async (tenant: string, body: { institutionId: string; bookId: string; copyId: string; borrowerUserId: string; dueDate: string }) =>
    (await api.post(`/tenants/${tenant}/library/circulations`, body)).data,
  returnBook: async (tenant: string, institution: string, circulationId: string) =>
    (await api.post(`/tenants/${tenant}/library/circulations/${circulationId}/return`, null, { params: { institutionId: institution } })).data,
  reservations: async (tenant: string, institution: string) =>
    (await api.get<LibraryReservation[]>(`/tenants/${tenant}/library/reservations`, { params: { institutionId: institution } })).data,
  reserveBook: async (tenant: string, body: { institutionId: string; bookId: string; borrowerUserId: string }) =>
    (await api.post(`/tenants/${tenant}/library/reservations`, body)).data,
  fines: async (tenant: string, institution: string) =>
    (await api.get<LibraryFine[]>(`/tenants/${tenant}/library/fines`, { params: { institutionId: institution } })).data,
  payFine: async (tenant: string, institution: string, fineId: string) =>
    (await api.post(`/tenants/${tenant}/library/fines/${fineId}/pay`, null, { params: { institutionId: institution } })).data,
  finePolicy: async (tenant: string, institution: string) =>
    (await api.get<FinePolicy>(`/tenants/${tenant}/library/fine-policy`, { params: { institutionId: institution } })).data,
  updateFinePolicy: async (tenant: string, body: {
    institutionId: string; gracePeriodDays: number; calculationMode: "FLAT_DAILY_AMOUNT";
    dailyAmount: number; dayBasis: "CALENDAR_DAYS" | "WORKING_DAYS"; fineCap: number; workingDays: number[];
  }) => (await api.put<FinePolicy>(`/tenants/${tenant}/library/fine-policy`, body)).data,
  dispatch: async (tenant: string, body: unknown) => (await api.post(`/tenants/${tenant}/communications/dispatch`, body)).data,
  routes: async (tenant: string, institution: string) =>
    (await api.get<TransportRoute[]>(`/tenants/${tenant}/transport/routes`, { params: { institutionId: institution } })).data,
  routeStops: async (tenant: string, institution: string, routeId: string) =>
    (await api.get<RouteStop[]>(`/tenants/${tenant}/transport/routes/${routeId}/stops`, { params: { institutionId: institution } })).data,
  createRoute: async (tenant: string, body: {
    institutionId: string; routeName: string; vehicleNumber: string; driverName: string; driverPhone: string;
    capacity: number; transportFeeCode: string; stops: { name: string; pickupTime: string; dropTime: string; feeAmount: number; order: number }[];
  }) => (await api.post(`/tenants/${tenant}/transport/routes`, body)).data,
  cancelRoute: async (tenant: string, institution: string, routeId: string) =>
    (await api.post(`/tenants/${tenant}/transport/routes/${routeId}/cancel`, null, { params: { institutionId: institution } })).data,
  allocations: async (tenant: string, institution: string) =>
    (await api.get<TransportAllocation[]>(`/tenants/${tenant}/transport/allocations`, { params: { institutionId: institution } })).data,
  allocate: async (tenant: string, body: { institutionId: string; routeId: string; stopId: string; passengerUserId: string }) =>
    (await api.post(`/tenants/${tenant}/transport/allocations`, body)).data,
  cancelAllocation: async (tenant: string, institution: string, id: string) =>
    (await api.post(`/tenants/${tenant}/transport/allocations/${id}/cancel`, null, { params: { institutionId: institution } })).data,
  dashboard: async (tenant: string, institution: string) =>
    (await api.get<Record<string, number | string | object>>(`/tenants/${tenant}/institutional-reports/executive-dashboard`, { params: { institutionId: institution } })).data,
};
