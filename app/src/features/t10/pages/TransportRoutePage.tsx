import { useCallback, useEffect, useState, type FormEvent } from "react";
import { PageHeader } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api, type RouteStop, type TransportAllocation, type TransportRoute } from "../api/t10Api";

type StopDraft = { name: string; pickupTime: string; dropTime: string; feeAmount: string; order: number };
const emptyStop = (order: number): StopDraft => ({ name: "", pickupTime: "", dropTime: "", feeAmount: "0", order });

export default function TransportRoutePage() {
  const tenant = useAppSelector((s) => s.auth.user?.tenantId ?? "");
  const institution = useAppSelector((s) => s.tenantPermissions.current?.institutionId ?? "");
  const actions = useAppSelector((s) => s.tenantPermissions.current?.allowedActions ?? []);
  const canManage = actions.includes("TRANSPORT_MANAGE");

  const [routes, setRoutes] = useState<TransportRoute[]>([]);
  const [allocations, setAllocations] = useState<TransportAllocation[]>([]);
  const [stops, setStops] = useState<RouteStop[]>([]);
  const [selectedRoute, setSelectedRoute] = useState("");
  const [passengerId, setPassengerId] = useState("");
  const [selectedStop, setSelectedStop] = useState("");
  const [routeName, setRouteName] = useState("");
  const [vehicleNumber, setVehicleNumber] = useState("");
  const [driverName, setDriverName] = useState("");
  const [driverPhone, setDriverPhone] = useState("");
  const [capacity, setCapacity] = useState("40");
  const [feeCode, setFeeCode] = useState("");
  const [stopDrafts, setStopDrafts] = useState<StopDraft[]>([emptyStop(0)]);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    if (!tenant || !institution) { setLoading(false); return; }
    setLoading(true); setError(""); setRoutes([]); setAllocations([]); setStops([]);
    try {
      setRoutes(await t10Api.routes(tenant, institution));
      if (canManage) setAllocations(await t10Api.allocations(tenant, institution));
    } catch {
      setError("Transport routes could not be loaded. Check institution permissions.");
    } finally { setLoading(false); }
  }, [tenant, institution, canManage]);

  const loadStops = useCallback(async (routeId: string) => {
    if (!tenant || !institution || !routeId) { setStops([]); setSelectedStop(""); return; }
    try {
      const result = await t10Api.routeStops(tenant, institution, routeId);
      setStops(result);
      setSelectedStop(result[0]?.id ?? "");
    } catch {
      setStops([]);
      setSelectedStop("");
      setError("Route stops could not be loaded.");
    }
  }, [tenant, institution]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { void loadStops(selectedRoute); }, [selectedRoute, loadStops]);

  async function submit(event: FormEvent, task: () => Promise<unknown>, success: string) {
    event.preventDefault();
    setBusy(true); setError(""); setNotice("");
    try { await task(); setNotice(success); await load(); }
    catch { setError("The transport operation failed. Check the route, stop, passenger and capacity."); }
    finally { setBusy(false); }
  }

  function updateStop(index: number, field: keyof StopDraft, value: string) {
    setStopDrafts((current) => current.map((stop, i) => i === index
      ? { ...stop, [field]: field === "order" ? Number(value) : value } : stop));
  }

  return <main className="app-page">
    <PageHeader title="Transport Tracking" description="Configure routes and stops, allocate passengers, and monitor seat capacity." />
    {loading && <p role="status">Loading transport routes and allocations…</p>}
    {error && <p role="alert">{error}</p>}
    {notice && <p role="status">{notice}</p>}

    <section className="app-panel">
      <h2>Routes and seat utilization</h2>
      <div className="app-table-wrap"><table><thead><tr><th>Route</th><th>Vehicle</th><th>Driver</th><th>Allocated / Capacity</th><th>Transport fee code</th>{canManage && <th>Actions</th>}</tr></thead>
        <tbody>{routes.map((route) => <tr key={route.id}>
          <td>{route.route_name}</td><td>{route.vehicle_number}</td><td>{route.driver_name}</td>
          <td>{route.allocated_seats} / {route.total_capacity}</td><td>{route.transport_fee_code ?? "—"}</td>
          {canManage && <td>{route.active ? <button type="button" disabled={busy} onClick={async () => { setBusy(true); setError(""); setNotice(""); try { await t10Api.cancelRoute(tenant,institution,route.id); setNotice("Route cancelled and active allocations released."); await load(); } catch { setError("Route could not be cancelled."); } finally { setBusy(false); } }}>Cancel route</button> : "Cancelled"}</td>}
        </tr>)}</tbody>
      </table></div>
      {!loading && routes.length === 0 && <p>No transport routes configured for this institution.</p>}
    </section>

    {canManage && <section className="app-panel">
      <h2>Create route</h2>
      <form className="form-grid" onSubmit={(e) => submit(e, () => t10Api.createRoute(tenant, {
        institutionId: institution, routeName, vehicleNumber, driverName, driverPhone,
        capacity: Number(capacity), transportFeeCode: feeCode,
        stops: stopDrafts.map((stop) => ({
          name: stop.name, pickupTime: stop.pickupTime, dropTime: stop.dropTime,
          feeAmount: Number(stop.feeAmount), order: stop.order,
        })),
      }), "Transport route created.")}>
        <label>Route name <input required value={routeName} onChange={(e) => setRouteName(e.target.value)} /></label>
        <label>Vehicle registration <input required value={vehicleNumber} onChange={(e) => setVehicleNumber(e.target.value.toUpperCase())} /></label>
        <label>Driver name <input required value={driverName} onChange={(e) => setDriverName(e.target.value)} /></label>
        <label>Driver phone <input value={driverPhone} onChange={(e) => setDriverPhone(e.target.value)} /></label>
        <label>Seat capacity <input required type="number" min="1" max="500" value={capacity} onChange={(e) => setCapacity(e.target.value)} /></label>
        <label>Transport fee-head code (optional) <input value={feeCode} onChange={(e) => setFeeCode(e.target.value.toUpperCase())} placeholder="Active TRANSPORT fee head" /></label>
        <h3>Route stops</h3>
        {stopDrafts.map((stop, index) => <fieldset key={index}>
          <legend>Stop {index + 1}</legend>
          <label>Stop name <input required value={stop.name} onChange={(e) => updateStop(index, "name", e.target.value)} /></label>
          <label>Pickup time <input type="time" value={stop.pickupTime} onChange={(e) => updateStop(index, "pickupTime", e.target.value)} /></label>
          <label>Drop time <input type="time" value={stop.dropTime} onChange={(e) => updateStop(index, "dropTime", e.target.value)} /></label>
          <label>Stop fee (INR) <input required type="number" min="0" step="0.01" value={stop.feeAmount} onChange={(e) => updateStop(index, "feeAmount", e.target.value)} /></label>
          <label>Order <input required type="number" min="0" value={stop.order} onChange={(e) => updateStop(index, "order", e.target.value)} /></label>
          {stopDrafts.length > 1 && <button type="button" onClick={() => setStopDrafts((current) => current.filter((_, i) => i !== index))}>Remove stop</button>}
        </fieldset>)}
        <button type="button" onClick={() => setStopDrafts((current) => [...current, emptyStop(current.length)])}>Add another stop</button>
        <button type="submit" disabled={busy || !routeName.trim() || !vehicleNumber.trim() || !driverName.trim() || stopDrafts.some((s) => !s.name.trim())}>Create route</button>
      </form>
    </section>}

    {canManage && <section className="app-panel">
      <h2>Allocate passenger</h2>
      <form className="form-grid" onSubmit={(e) => submit(e, () => t10Api.allocate(tenant, {
        institutionId: institution, routeId: selectedRoute, stopId: selectedStop, passengerUserId: passengerId,
      }), "Passenger allocated to the route.")}>
        <label>Route <select required value={selectedRoute} onChange={(e) => setSelectedRoute(e.target.value)}>
          <option value="">Select route</option>{routes.map((route) => <option key={route.id} value={route.id}>{route.route_name} ({route.allocated_seats}/{route.total_capacity})</option>)}
        </select></label>
        <label>Pickup stop <select required value={selectedStop} onChange={(e) => setSelectedStop(e.target.value)}>
          <option value="">Select stop</option>{stops.map((stop) => <option key={stop.id} value={stop.id}>{stop.stop_order + 1}. {stop.stop_name} — INR {Number(stop.fee_amount).toFixed(2)}</option>)}
        </select></label>
        <label>Passenger user ID <input required value={passengerId} onChange={(e) => setPassengerId(e.target.value)} /></label>
        <button type="submit" disabled={busy || !selectedRoute || !selectedStop || !passengerId.trim()}>Allocate seat</button>
      </form>
      <p>Capacity is enforced transactionally by the database. A full route rejects additional allocations.</p>
    </section>}

    {canManage && <section className="app-panel">
      <h2>Passenger allocations</h2>
      <div className="app-table-wrap"><table><thead><tr><th>Route</th><th>Stop</th><th>Passenger</th><th>Stop fee</th><th>Status</th><th>Action</th></tr></thead>
        <tbody>{allocations.map((allocation) => <tr key={allocation.id}>
          <td>{allocation.route_name}</td><td>{allocation.stop_name}</td><td>{allocation.passenger_user_id}</td>
          <td>INR {Number(allocation.fee_amount).toFixed(2)}</td><td>{allocation.status}</td>
          <td>{allocation.status === "ACTIVE" && <button type="button" disabled={busy} onClick={() => {
            setBusy(true); setError(""); setNotice("");
            void t10Api.cancelAllocation(tenant, institution, allocation.id).then(async () => { setNotice("Transport allocation cancelled."); await load(); })
              .catch(() => setError("Allocation cancellation failed.")).finally(() => setBusy(false));
          }}>Cancel</button>}</td>
        </tr>)}</tbody>
      </table></div>
      {!loading && allocations.length === 0 && <p>No passenger allocations recorded.</p>}
    </section>}
  </main>;
}
