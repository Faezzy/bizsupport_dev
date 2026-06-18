-- V6: Вспомогательный модуль кэша AI-анализа тендеров

CREATE TABLE IF NOT EXISTS tender_analysis_cache (
    id         BIGSERIAL PRIMARY KEY,
    tender_id  BIGINT       NOT NULL REFERENCES tenders(id) ON DELETE CASCADE,
    model      VARCHAR(100) NOT NULL,
    analysis   TEXT         NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_analysis_tender ON tender_analysis_cache(tender_id);
