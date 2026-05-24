CREATE TABLE IF NOT EXISTS holdings (
    id          SERIAL PRIMARY KEY,
    symbol      VARCHAR(16)     NOT NULL,
    quantity    NUMERIC(20, 8)  NOT NULL,
    cost_basis  NUMERIC(20, 8)  NOT NULL,
    notes       VARCHAR(512),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_holdings_symbol ON holdings (symbol);
