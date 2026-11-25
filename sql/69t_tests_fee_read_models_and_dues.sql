-- T8 database contract checks for migration 69.
DO $$
DECLARE v_def text;
BEGIN
    IF to_regprocedure('app.fn_fee_balance(uuid,uuid)') IS NULL THEN
        RAISE EXCEPTION 'T8-DB-01: app.fn_fee_balance(uuid,uuid) is missing';
    END IF;
    IF to_regclass('app.v_fee_invoice') IS NULL THEN
        RAISE EXCEPTION 'T8-DB-02: app.v_fee_invoice read model is missing';
    END IF;
    IF to_regclass('app.fee_invoice') IS NOT NULL OR to_regclass('app.fee_payment') IS NOT NULL THEN
        RAISE EXCEPTION 'T8-DB-03: parallel invoice/payment source tables must not be created';
    END IF;
    SELECT pg_get_viewdef('app.v_student_dues_flag'::regclass, true) INTO v_def;
    IF position('signed_balance' IN v_def) = 0 OR position('>' IN v_def) = 0 THEN
        RAISE EXCEPTION 'T8-DB-04: dues flag must reflect positive outstanding balance';
    END IF;
    SELECT pg_get_functiondef('app.fn_allocation_guard()'::regprocedure) INTO v_def;
    IF position('PARTIAL_PAYMENT_NOT_ALLOWED' IN v_def) = 0 THEN
        RAISE EXCEPTION 'T8-DB-05: full-installment payment guard was weakened or replaced';
    END IF;
    IF position('REFUND' IN v_def) > 0 THEN
        RAISE EXCEPTION 'T8-DB-06: allocation guard must not introduce refund handling';
    END IF;
    SELECT pg_get_functiondef('app.fn_fee_balance(uuid,uuid)'::regprocedure) INTO v_def;
    IF position('v_student_fee_balance' IN v_def) = 0 THEN
        RAISE EXCEPTION 'T8-DB-07: fee balance must derive from the existing balance view';
    END IF;
END $$;
