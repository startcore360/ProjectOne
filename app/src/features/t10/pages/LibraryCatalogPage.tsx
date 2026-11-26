import { useCallback, useEffect, useState, type FormEvent } from "react";
import { PageHeader } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import {
  t10Api, type BookCopy, type CatalogBook, type Circulation, type FinePolicy,
  type LibraryFine, type LibraryReservation,
} from "../api/t10Api";

const weekdays = [{ id: 1, label: "Mon" }, { id: 2, label: "Tue" }, { id: 3, label: "Wed" },
  { id: 4, label: "Thu" }, { id: 5, label: "Fri" }, { id: 6, label: "Sat" }, { id: 7, label: "Sun" }];

export default function LibraryCatalogPage() {
  const tenant = useAppSelector((s) => s.auth.user?.tenantId ?? "");
  const institution = useAppSelector((s) => s.tenantPermissions.current?.institutionId ?? "");
  const actions = useAppSelector((s) => s.tenantPermissions.current?.allowedActions ?? []);
  const can = useCallback((action: string) => actions.includes(action), [actions]);

  const [query, setQuery] = useState("");
  const [books, setBooks] = useState<CatalogBook[]>([]);
  const [copies, setCopies] = useState<BookCopy[]>([]);
  const [circulations, setCirculations] = useState<Circulation[]>([]);
  const [fines, setFines] = useState<LibraryFine[]>([]);
  const [reservations, setReservations] = useState<LibraryReservation[]>([]);
  const [policy, setPolicy] = useState<FinePolicy | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const [title, setTitle] = useState("");
  const [author, setAuthor] = useState("");
  const [isbn, setIsbn] = useState("");
  const [category, setCategory] = useState("");
  const [copyCount, setCopyCount] = useState("1");
  const [manageBookId, setManageBookId] = useState("");
  const [accessionNumber, setAccessionNumber] = useState("");
  const [managedCopies, setManagedCopies] = useState<BookCopy[]>([]);

  const [issueBookId, setIssueBookId] = useState("");
  const [issueCopyId, setIssueCopyId] = useState("");
  const [borrowerId, setBorrowerId] = useState("");
  const [dueDate, setDueDate] = useState("");

  const [reserveBookId, setReserveBookId] = useState("");
  const [reserveBorrowerId, setReserveBorrowerId] = useState("");

  const [grace, setGrace] = useState("0");
  const [dailyAmount, setDailyAmount] = useState("5");
  const [dayBasis, setDayBasis] = useState<"CALENDAR_DAYS" | "WORKING_DAYS">("CALENDAR_DAYS");
  const [cap, setCap] = useState("500");
  const [workingDays, setWorkingDays] = useState<number[]>([1, 2, 3, 4, 5, 6]);

  const load = useCallback(async () => {
    if (!tenant || !institution) return;
    setError("");
    try {
      const catalog = await t10Api.catalog(tenant, institution, query);
      setBooks(catalog);
      if (can("BOOK_ISSUE_RETURN")) setCirculations(await t10Api.circulations(tenant, institution));
      if (can("RESERVATION_MANAGE")) setReservations(await t10Api.reservations(tenant, institution));
      if (can("FINE_COLLECT")) setFines(await t10Api.fines(tenant, institution));
      if (can("LIBRARY_SETTINGS_SET")) {
        const current = await t10Api.finePolicy(tenant, institution);
        setPolicy(current);
        setGrace(String(current.grace_period_days));
        setDailyAmount(String(current.daily_amount));
        setDayBasis(current.day_basis);
        setCap(String(current.fine_cap));
        const parsed = Array.isArray(current.working_days) ? current.working_days :
          typeof current.working_days === "string" ? JSON.parse(current.working_days) as number[] : [];
        setWorkingDays(parsed);
      }
    } catch {
      setError("Library data could not be loaded. Check your institution access and try again.");
    }
  }, [tenant, institution, query, can]);

  useEffect(() => { void load(); }, [load]);

  const loadCopies = useCallback(async (bookId: string) => {
    if (!tenant || !institution || !bookId) { setCopies([]); return; }
    try {
      const result = await t10Api.copies(tenant, institution, bookId);
      const available = result.filter((copy) => copy.status === "AVAILABLE");
      setCopies(available);
      setIssueCopyId(available[0]?.id ?? "");
    } catch {
      setCopies([]);
      setIssueCopyId("");
    }
  }, [tenant, institution]);

  useEffect(() => { void loadCopies(issueBookId); }, [issueBookId, loadCopies]);

  const loadManagedCopies = useCallback(async () => {
    if (!tenant || !institution || !manageBookId) { setManagedCopies([]); return; }
    try { setManagedCopies(await t10Api.copies(tenant, institution, manageBookId)); }
    catch { setManagedCopies([]); setError("Book copies could not be loaded."); }
  }, [tenant, institution, manageBookId]);
  useEffect(() => { void loadManagedCopies(); }, [loadManagedCopies]);

  async function submit(event: FormEvent, task: () => Promise<unknown>, success: string) {
    event.preventDefault();
    setBusy(true); setError(""); setNotice("");
    try { await task(); setNotice(success); await load(); }
    catch { setError("The operation could not be completed. Verify the entered IDs and permissions."); }
    finally { setBusy(false); }
  }

  const policyValue = policy;
  return <main className="app-page">
    <PageHeader title="Library Operations" description="Catalog, circulation, reservations and institution-configured overdue fines." />
    {error && <p role="alert">{error}</p>}
    {notice && <p role="status">{notice}</p>}

    <section className="app-panel">
      <h2>Catalog</h2>
      <label>Search title or ISBN <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search books" /></label>
      {can("CATALOG_MANAGE") && <form className="form-grid" onSubmit={(e) => submit(e, () => t10Api.addBook(tenant, {
        institutionId: institution, title, author, isbn, category, copies: Number(copyCount),
      }), "Book and copies added to the catalog.")}>
        <h3>Add a book</h3>
        <label>Title <input required value={title} onChange={(e) => setTitle(e.target.value)} /></label>
        <label>Author <input value={author} onChange={(e) => setAuthor(e.target.value)} /></label>
        <label>ISBN <input value={isbn} onChange={(e) => setIsbn(e.target.value)} /></label>
        <label>Category <input value={category} onChange={(e) => setCategory(e.target.value)} /></label>
        <label>Number of copies <input required type="number" min="0" max="500" value={copyCount} onChange={(e) => setCopyCount(e.target.value)} /></label>
        <button disabled={busy || !title.trim()} type="submit">Add catalog item</button>
      </form>}
      <div className="app-table-wrap"><table><thead><tr><th>Title</th><th>Author</th><th>ISBN</th><th>Available</th><th>Total</th></tr></thead>
        <tbody>{books.map((book) => <tr key={book.id}><td>{book.title}</td><td>{book.author ?? "—"}</td><td>{book.isbn ?? "—"}</td><td>{book.available_copies}</td><td>{book.total_copies}</td></tr>)}</tbody>
      </table></div>
      {books.length === 0 && <p>No catalog matches. Add a book if you have catalog permissions.</p>}
      {can("CATALOG_MANAGE") && <section className="app-subpanel">
        <h3>Manage individual copies</h3>
        <label>Catalog item <select value={manageBookId} onChange={(e) => setManageBookId(e.target.value)}>
          <option value="">Select a book</option>{books.map((book) => <option key={book.id} value={book.id}>{book.title}</option>)}
        </select></label>
        <form className="form-grid" onSubmit={(e) => submit(e, async () => {
          await t10Api.addCopy(tenant, manageBookId, { institutionId: institution, accessionNumber });
          setAccessionNumber(""); await loadManagedCopies();
        }, "Book copy added.")}>
          <label>Accession number <input required maxLength={80} value={accessionNumber} onChange={(e) => setAccessionNumber(e.target.value)} /></label>
          <button type="submit" disabled={busy || !manageBookId || !accessionNumber.trim()}>Add copy</button>
        </form>
        {managedCopies.length === 0 ? <p>No individual copies recorded for this item.</p> :
          <ul>{managedCopies.map((copy) => <li key={copy.id}>{copy.accession_number} — {copy.status}
            {copy.status === "AVAILABLE" && <button type="button" disabled={busy} onClick={() => {
              setBusy(true); setError(""); setNotice("");
              void t10Api.withdrawCopy(tenant, institution, copy.id).then(async () => { setNotice("Available copy withdrawn."); await loadManagedCopies(); await load(); })
                .catch(() => setError("Copy could not be withdrawn. Only available copies can be withdrawn.")).finally(() => setBusy(false));
            }}>Withdraw</button>}
          </li>)}</ul>}
      </section>}
    </section>

    {can("BOOK_ISSUE_RETURN") && <section className="app-panel">
      <h2>Issue a copy</h2>
      <form className="form-grid" onSubmit={(e) => submit(e, () => t10Api.issueBook(tenant, {
        institutionId: institution, bookId: issueBookId, copyId: issueCopyId,
        borrowerUserId: borrowerId, dueDate,
      }), "Book copy issued.")}>
        <label>Book <select required value={issueBookId} onChange={(e) => setIssueBookId(e.target.value)}>
          <option value="">Select a book</option>{books.filter((b) => b.available_copies > 0).map((b) => <option key={b.id} value={b.id}>{b.title} ({b.available_copies} available)</option>)}
        </select></label>
        <label>Available copy <select required value={issueCopyId} onChange={(e) => setIssueCopyId(e.target.value)}>
          <option value="">Select copy</option>{copies.map((copy) => <option key={copy.id} value={copy.id}>{copy.accession_number}</option>)}
        </select></label>
        <label>Borrower user ID <input required value={borrowerId} onChange={(e) => setBorrowerId(e.target.value)} /></label>
        <label>Due date <input required type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} /></label>
        <button type="submit" disabled={busy || !issueCopyId}>Issue book</button>
      </form>
    </section>}

    {can("BOOK_ISSUE_RETURN") && <section className="app-panel">
      <h2>Circulation register</h2>
      <div className="app-table-wrap"><table><thead><tr><th>Book / Copy</th><th>Borrower</th><th>Due</th><th>Status</th><th>Accrued fine</th><th>Action</th></tr></thead>
        <tbody>{circulations.map((item) => <tr key={item.id}><td>{item.title} / {item.accession_number ?? "—"}</td><td>{item.borrower_user_id}</td><td>{item.due_date}</td><td>{item.status}</td><td>{Number(item.current_fine).toFixed(2)}</td><td>{!item.returned_at && <button type="button" disabled={busy} onClick={() => {
          setBusy(true); setError(""); setNotice("");
          void t10Api.returnBook(tenant, institution, item.id).then(async () => { setNotice("Book returned; applicable overdue fine assessed."); await load(); })
            .catch(() => setError("Book return failed.")).finally(() => setBusy(false));
        }}>Return</button>}</td></tr>)}</tbody>
      </table></div>
    </section>}

    {can("RESERVATION_MANAGE") && <section className="app-panel">
      <h2>Reservations</h2>
      <form className="form-grid" onSubmit={(e) => submit(e, () => t10Api.reserveBook(tenant, {
        institutionId: institution, bookId: reserveBookId, borrowerUserId: reserveBorrowerId,
      }), "Reservation added to the queue.")}>
        <label>Book <select required value={reserveBookId} onChange={(e) => setReserveBookId(e.target.value)}>
          <option value="">Select a book</option>{books.map((b) => <option key={b.id} value={b.id}>{b.title}</option>)}
        </select></label>
        <label>Borrower user ID <input required value={reserveBorrowerId} onChange={(e) => setReserveBorrowerId(e.target.value)} /></label>
        <button type="submit" disabled={busy}>Reserve book</button>
      </form>
      <ul>{reservations.map((r) => <li key={r.id}>{r.title} — queue #{r.queue_position} — {r.borrower_user_id}</li>)}</ul>
    </section>}

    {can("LIBRARY_SETTINGS_SET") && <section className="app-panel">
      <h2>Overdue fine policy</h2>
      {policyValue?.is_placeholder_default && <p role="note">This is an unapproved placeholder policy. Review and save the institution's actual policy before relying on fine amounts.</p>}
      <form className="form-grid" onSubmit={(e) => submit(e, () => t10Api.updateFinePolicy(tenant, {
        institutionId: institution, gracePeriodDays: Number(grace), calculationMode: "FLAT_DAILY_AMOUNT",
        dailyAmount: Number(dailyAmount), dayBasis, fineCap: Number(cap), workingDays,
      }), "Fine policy saved.")}>
        <label>Grace period (days) <input required type="number" min="0" value={grace} onChange={(e) => setGrace(e.target.value)} /></label>
        <label>Daily fine amount (INR) <input required type="number" min="0" step="0.01" value={dailyAmount} onChange={(e) => setDailyAmount(e.target.value)} /></label>
        <label>Day basis <select value={dayBasis} onChange={(e) => setDayBasis(e.target.value as "CALENDAR_DAYS" | "WORKING_DAYS")}>
          <option value="CALENDAR_DAYS">Calendar days</option><option value="WORKING_DAYS">Selected working days</option>
        </select></label>
        <label>Fine cap (INR) <input required type="number" min="0" step="0.01" value={cap} onChange={(e) => setCap(e.target.value)} /></label>
        {dayBasis === "WORKING_DAYS" && <fieldset><legend>Working days</legend>{weekdays.map((day) => <label key={day.id}>
          <input type="checkbox" checked={workingDays.includes(day.id)} onChange={(e) => setWorkingDays((current) =>
            e.target.checked ? [...current, day.id].sort() : current.filter((d) => d !== day.id))} /> {day.label}
        </label>)}</fieldset>}
        <button type="submit" disabled={busy || (dayBasis === "WORKING_DAYS" && workingDays.length === 0)}>Save fine policy</button>
      </form>
    </section>}

    {can("FINE_COLLECT") && <section className="app-panel">
      <h2>Outstanding assessed fines</h2>
      <div className="app-table-wrap"><table><thead><tr><th>Book</th><th>Borrower</th><th>Fine</th><th>Outstanding</th><th>Action</th></tr></thead>
        <tbody>{fines.map((fine) => <tr key={fine.id}><td>{fine.title}</td><td>{fine.borrower_user_id}</td><td>{Number(fine.amount).toFixed(2)}</td><td>{Number(fine.outstanding).toFixed(2)}</td><td>
          <button type="button" disabled={busy} onClick={() => {
            setBusy(true); setError(""); setNotice("");
            void t10Api.payFine(tenant, institution, fine.id).then(async () => { setNotice("Fine paid in full."); await load(); })
              .catch(() => setError("Fine payment could not be recorded.")).finally(() => setBusy(false));
          }}>Collect full fine</button>
        </td></tr>)}</tbody>
      </table></div>
      {fines.length === 0 && <p>No assessed outstanding fines.</p>}
    </section>}
  </main>;
}
