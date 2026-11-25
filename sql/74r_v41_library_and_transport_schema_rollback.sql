BEGIN;
DROP VIEW IF EXISTS app.v_student_library_dues_flag;
DROP TRIGGER IF EXISTS trg_transport_capacity_guard ON app.transport_allocation;
DROP FUNCTION IF EXISTS app.fn_transport_capacity_guard();
DROP TABLE IF EXISTS app.transport_allocation,app.route_stop,app.transport_route;
DROP TABLE IF EXISTS app.library_reservation,app.library_fine,app.book_circulation,app.library_fine_policy,app.book_copy,app.library_book;
COMMIT;
