CREATE TABLE accounts (
    account_number VARCHAR(30) PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(18, 2) NOT NULL CHECK (balance >= 0)
);

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    client_reference VARCHAR(80) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    source_account VARCHAR(30) NOT NULL,
    destination_account VARCHAR(30) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO accounts (account_number, status, currency, balance) VALUES
    ('ACC-1001', 'ACTIVE', 'COP', 800000.00),
    ('ACC-1002', 'ACTIVE', 'COP', 200000.00),
    ('ACC-1003', 'BLOCKED', 'COP', 500000.00);
