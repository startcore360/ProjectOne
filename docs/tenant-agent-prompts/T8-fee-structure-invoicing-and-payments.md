# Chat T8 — Fee Structure, Quota Invoicing, Online Payments & Webhooks

You are a senior full-stack developer (Java 21 / Spring Boot 3.4.3, React 19 / TypeScript 5.8, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-2-development`.

## Your mission

Deliver the complete Student Financial and Fee Management engine (**Module 8: Actions 88–109**). You own fee heads, quota-based fee structures, installment schedule generation, automated late fee calculation, two-step concession approvals, online payment gateway integration (Razorpay & Stripe), cryptographic HMAC webhook reconciliation, and official printable tax receipts.

---

## Your stories

Your acceptance criteria are **every bullet, rule, and table cell for these story IDs in `docs/TENANT_COMPLETION_PLAN.md`**, word for word.

| ID | Action Code(s) | What | Plan Section |
|---|---|---|---|
| **T8-S1** | `ACT-088`..`ACT-092` | Fee Head & Structure Configuration: fee categories (Tuition, Lab, Library, Exam, Transport), program-level structures, quota variants (Merit, General, Management), and installment frequency. | §7.8 |
| **T8-S2** | `ACT-093`..`ACT-097` | Installment Invoicing & Automated Late Fine Engine: bulk invoice generation for enrolled students, installment due dates, and customizable late fine policy (Decision PO-T03: configurable grace days, flat daily or monthly percentage, fine caps). | §7.8 |
| **T8-S3** | `ACT-098`..`ACT-101` | Fee Concession & Scholarship Workflow: Approval hierarchy (Decision PO-T04: Management/Trustee first; rolls down to Principal if Management unassigned) with statutory concession audit ledger. | §7.8 |
| **T8-S4** | `ACT-102`..`ACT-105` | Online Payment Integration & HMAC Webhooks: student checkout modal (Razorpay / Stripe), HMAC-SHA256 signature verification in webhook controller, and idempotent invoice reconciliation. | §7.8 |
| **T8-S5** | `ACT-106`..`ACT-109` | Offline Payments, Refunds & Receipts: counter payment collection (Cash, DD, Cheque), partial payments, refund disbursements, and PDF receipt generation. | §7.8 |

---

### Detailed Technical Specifications

#### Story T8-S1 & T8-S2: Fee Structures & Installment Invoicing (M08)
- **Database Tables:**
  - `app.fee_head` (`id`, `tenant_id`, `institution_id`, `name`, `code`, `is_refundable`, `is_optional`).
  - `app.fee_structure` (`id`, `tenant_id`, `institution_id`, `program_id`, `academic_year_id`, `quota_category`, `total_annual_amount`, `installments_count`).
  - `app.fee_invoice` (`id`, `tenant_id`, `student_id`, `invoice_number`, `installment_number`, `due_date`, `base_amount`, `concession_amount`, `late_fine_amount`, `paid_amount`, `status` [DRAFT, ISSUED, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED]).
- **Automated Late Fine Engine:**
  - `LateFeeCalculationScheduler.java` runs nightly:
    - Finds invoices where `due_date < CURRENT_DATE` and `status IN ('ISSUED', 'PARTIALLY_PAID')`.
    - Computes late fine: $\text{Late Fine} = \text{Days Overdue} \times \text{Daily Fine Rate}$ (capped at maximum threshold).
    - Updates `late_fine_amount` and marks `status = 'OVERDUE'`.

#### Story T8-S3: Fee Concessions
- **Database Tables:**
  - `app.fee_concession` (`id`, `tenant_id`, `student_id`, `invoice_id`, `concession_type`, `discount_percentage`, `discount_amount`, `reason`, `status` [REQUESTED, APPROVED, REJECTED], `approved_by_user_id`).
- **Two-Step Approval:**
  - Step 1: Accountant reviews documentation.
  - Step 2: Principal / Management approves; concession is atomically deducted from invoice balance.

#### Story T8-S4 & T8-S5: Payment Gateway Webhooks & Receipts
- **Database Tables:**
  - `app.fee_payment` (`id`, `tenant_id`, `invoice_id`, `payment_reference`, `amount`, `payment_mode` [ONLINE_RAZORPAY, ONLINE_STRIPE, CASH, DD, CHEQUE], `gateway_transaction_id`, `gateway_signature`, `paid_at`, `receipt_number`).
  - `app.payment_webhook_log` (`id`, `gateway`, `event_type`, `payload_json`, `signature`, `is_verified`, `processed_at`).
- **Cryptographic Webhook Verification:**
  - `PaymentWebhookController.java` (`POST /api/webhooks/payments/razorpay`, `POST /api/webhooks/payments/stripe`):
    - Computes expected HMAC-SHA256 signature using tenant gateway secret.
    - If signature does not match, returns HTTP 401 `INVALID_GATEWAY_SIGNATURE`.
    - If valid, idempotently records payment, generates unique sequential `receipt_number`, updates invoice status, and writes audit outbox.
- **Frontend:**
  - `FeePaymentPortalPage.tsx`: Student dues summary, checkout button triggering Razorpay SDK modal, and downloadable PDF receipt with digital watermark.

---

## Database migrations you own

Use **only** numbers **69** and **70**.
- `69_v41_fee_structure_and_invoices.sql`: `app.fee_head`, `app.fee_structure`, `app.fee_invoice`.
- `70_v41_fee_payments_and_webhooks.sql`: `app.fee_payment`, `app.fee_concession`, `app.payment_webhook_log`.

---

## Files you own

- **Backend:**
  - `com.edu360.api.finance.controller.FeeStructureController`
  - `com.edu360.api.finance.controller.FeeInvoiceController`
  - `com.edu360.api.finance.controller.FeePaymentController`
  - `com.edu360.api.finance.controller.PaymentWebhookController`
  - `com.edu360.api.finance.service.FeeBillingService`
  - `com.edu360.api.finance.service.PaymentReconciliationService`
  - `com.edu360.api.finance.scheduler.LateFeeCalculationScheduler`
- **Frontend:**
  - `app/src/features/finance/pages/FeeStructureManagementPage.tsx`
  - `app/src/features/finance/pages/StudentFeeInvoicesPage.tsx`
  - `app/src/features/finance/pages/FeePaymentPortalPage.tsx`
  - `app/src/features/finance/components/RazorpayCheckoutModal.tsx`
  - `app/src/features/finance/components/PrintableFeeReceipt.tsx`
- **Tests:**
  - `api/src/test/java/com/edu360/api/finance/PaymentWebhookSecurityIT.java`
  - `api/src/test/java/com/edu360/api/finance/LateFeeCalculationIT.java`
  - `app/src/features/finance/__tests__/FeePaymentPortalPage.test.tsx`

---

## Depends on / others depend on you

- **You depend on:** Chat T4 for student records and Chat T2 for programs/quotas.
- **Others depend on you:** Chat T7 (Examinations) queries your invoice balance in `fn_publish_gate` before releasing report cards.

---

## Verification you must run and show before pushing

1. `cd api && .\mvnw.cmd test -Dtest=PaymentWebhookSecurityIT,LateFeeCalculationIT`
2. `cd app && npm test -- FeePaymentPortalPage`
3. `npm run lint && npm run build`
