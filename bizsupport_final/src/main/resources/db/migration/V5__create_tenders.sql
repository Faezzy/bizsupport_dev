-- ═══════════════════════════════════════════════════════════════
-- V5: Создание таблицы тендеров
-- Скопируйте в src/main/resources/db/migration/
-- Если у вас уже есть V5__... — переименуйте этот файл в V6__... и т.д.
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS tenders (
    id                   BIGSERIAL PRIMARY KEY,
    registry_number      VARCHAR(30)  NOT NULL UNIQUE,
    title                VARCHAR(1000) NOT NULL,
    description          TEXT,
    customer_name        VARCHAR(500) NOT NULL,
    customer_inn         VARCHAR(12),
    law_type             VARCHAR(20)  NOT NULL,
    procurement_method   VARCHAR(100),
    initial_price        NUMERIC(15,2),
    currency             VARCHAR(3),
    region               VARCHAR(200),
    okpd_code            VARCHAR(20),
    category             VARCHAR(100),
    published_at         TIMESTAMP,
    submission_deadline  TIMESTAMP,
    auction_date         DATE,
    application_security NUMERIC(15,2),
    contract_security    NUMERIC(15,2),
    msp_only             BOOLEAN DEFAULT FALSE,
    status               VARCHAR(30)  NOT NULL,
    source_url           VARCHAR(500),
    source               VARCHAR(50),
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tender_law      ON tenders(law_type);
CREATE INDEX idx_tender_status   ON tenders(status);
CREATE INDEX idx_tender_region   ON tenders(region);
CREATE INDEX idx_tender_deadline ON tenders(submission_deadline);

-- Mock-данные подгружаются автоматически через MockTenderProvider.fetchInitial()
-- при первом старте приложения (если таблица пустая).
-- Управляется флагом app.tenders.auto-load=true в application.properties.
