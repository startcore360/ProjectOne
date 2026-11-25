-- T7 database contract checks for migration 68.
DO $$
DECLARE v_def text; v_default text;
BEGIN
    IF to_regclass('app.exam_cycle') IS NULL OR to_regclass('app.exam_schedule') IS NULL
       OR to_regclass('app.hall_ticket') IS NULL OR to_regclass('app.exam_mark') IS NULL
       OR to_regclass('app.report_card') IS NULL OR to_regclass('app.grade_scale') IS NULL THEN
        RAISE EXCEPTION 'T7-DB-68-01: examination and results tables are incomplete';
    END IF;
    SELECT column_default INTO v_default FROM information_schema.columns
     WHERE table_schema='app' AND table_name='hall_ticket' AND column_name='is_eligible';
    IF v_default IS NULL OR v_default NOT IN ('false','false::boolean') THEN
        RAISE EXCEPTION 'T7-DB-68-02: hall tickets must default to ineligible';
    END IF;
    IF to_regprocedure('app.fn_publish_gate(uuid,uuid)') IS NULL THEN
        RAISE EXCEPTION 'T7-DB-68-03: report-card publish gate is missing';
    END IF;
    SELECT pg_get_functiondef('app.fn_publish_gate(uuid,uuid)'::regprocedure) INTO v_def;
    IF position('PUBLISH_GATE_BLOCKED_FEE_BALANCE_UNAVAILABLE' IN v_def) = 0
       OR position('PUBLISH_GATE_BLOCKED_MODERATION' IN v_def) = 0 THEN
        RAISE EXCEPTION 'T7-DB-68-04: report-card gate must fail closed on unavailable fees and pending moderation';
    END IF;
    IF to_regprocedure('app.fn_fee_balance(uuid,uuid)') IS NULL THEN
        RAISE EXCEPTION 'T7-DB-68-05: T8 fee-balance integration must be present before report-card publishing is enabled';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='app.exam_mark'::regclass
                    AND tgname='trg_exam_mark_term_lock' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'T7-DB-68-06: term-lock trigger on exam marks is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='app.report_card'::regclass
                    AND tgname='trg_report_card_publish_gate' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'T7-DB-68-07: report-card publish trigger is missing';
    END IF;
END $$;
