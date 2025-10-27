CREATE TABLE IF NOT EXISTS filing (
                                      id BIGSERIAL PRIMARY KEY,
                                      title VARCHAR(1024),
    link VARCHAR(2048),
    summary TEXT,
    row_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- Trigger function to auto-update updated_at
CREATE OR REPLACE FUNCTION update_filing_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_filing_updated_at
    BEFORE UPDATE ON filing
    FOR EACH ROW
    EXECUTE FUNCTION update_filing_updated_at();
