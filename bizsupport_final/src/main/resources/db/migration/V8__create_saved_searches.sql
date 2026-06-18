-- V8: Сохранённые поиски по тендерам
-- Пользователь сохраняет набор фильтров; планировщик проверяет
-- новые совпадения и создаёт уведомление при появлении новых тендеров.

CREATE TABLE saved_searches (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name             VARCHAR(200) NOT NULL,
    query            VARCHAR(500),
    law_type         VARCHAR(10),
    price_from       NUMERIC(15, 2),
    price_to         NUMERIC(15, 2),
    region           VARCHAR(200),
    msp_only         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_checked_at  TIMESTAMP,
    last_result_count INT         NOT NULL DEFAULT -1
);

CREATE INDEX idx_saved_searches_user ON saved_searches(user_id);
