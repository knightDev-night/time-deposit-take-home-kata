CREATE TABLE time_deposits (
    id INTEGER PRIMARY KEY,
    plan_type TEXT NOT NULL,
    days INTEGER NOT NULL,
    balance NUMERIC NOT NULL
);

CREATE TABLE withdrawals (
    id INTEGER PRIMARY KEY,
    time_deposit_id INTEGER NOT NULL REFERENCES time_deposits (id),
    amount NUMERIC NOT NULL,
    date DATE NOT NULL
);

CREATE INDEX withdrawals_time_deposit_id_idx ON withdrawals (time_deposit_id);
