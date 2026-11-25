BEGIN;

INSERT INTO time_deposits (id, plan_type, days, balance) VALUES
    (101, 'basic', 30, 1200.00),
    (102, 'basic', 31, 2400.00),
    (103, 'student', 365, 6.00),
    (104, 'student', 366, 1800.125),
    (105, 'premium', 45, 600.00),
    (106, 'premium', 46, 4800.00);

INSERT INTO withdrawals (id, time_deposit_id, amount, date) VALUES
    (201, 102, 20.00, DATE '2026-11-01'),
    (202, 106, 125.50, DATE '2026-11-02'),
    (203, 106, 26.125, DATE '2026-11-12');

COMMIT;
