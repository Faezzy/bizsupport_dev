-- ============================================================
-- V2__seed_legal_references.sql
-- Начальные данные: правовые ссылки для налоговых режимов и госзакупок
-- ============================================================

-- ── Налоговые режимы (entity_type = 'TAX_REGIME') ─────────────

-- УСН 6% (id=1)
INSERT INTO legal_references (entity_type, entity_id, title, url, article) VALUES
('TAX_REGIME', 1, 'Глава 26.2 НК РФ — Упрощённая система налогообложения',
 'https://www.consultant.ru/document/cons_doc_LAW_28165/2a8e0fca0db0f89c08e98e098e77fa2b77e9dcec/',
 'ст. 346.11–346.25 НК РФ'),
('TAX_REGIME', 1, 'Ставка 6% при объекте «доходы»',
 'https://www.consultant.ru/document/cons_doc_LAW_28165/0a66b66e8e16eb8b6eb560ea5bc81e8b71980920/',
 'ст. 346.20 НК РФ'),
('TAX_REGIME', 1, 'Порядок исчисления и уплаты налога',
 'https://www.consultant.ru/document/cons_doc_LAW_28165/9d31bac86db6e67bdd3a0e8cafab4f498db0dfc7/',
 'ст. 346.21 НК РФ');

-- УСН 15% (id=2)
INSERT INTO legal_references (entity_type, entity_id, title, url, article) VALUES
('TAX_REGIME', 2, 'Глава 26.2 НК РФ — Упрощённая система налогообложения',
 'https://www.consultant.ru/document/cons_doc_LAW_28165/2a8e0fca0db0f89c08e98e098e77fa2b77e9dcec/',
 'ст. 346.11–346.25 НК РФ'),
('TAX_REGIME', 2, 'Определение расходов при УСН',
 'https://www.consultant.ru/document/cons_doc_LAW_28165/fcf46d6a0e1a64be643badc0682e03a7a3f97966/',
 'ст. 346.16 НК РФ'),
('TAX_REGIME', 2, 'Минимальный налог 1% от доходов',
 'https://www.consultant.ru/document/cons_doc_LAW_28165/9ce0dbec1e217651d93f0cb416a1a5b62d62c08e/',
 'ст. 346.18 п. 6 НК РФ');

-- ОСНО (id=3)
INSERT INTO legal_references (entity_type, entity_id, title, url, article) VALUES
('TAX_REGIME', 3, 'Глава 21 НК РФ — Налог на добавленную стоимость',
 'https://www.consultant.ru/document/cons_doc_LAW_28165/4f41fe599ce341751e4e34dc50a4b676674c1416/',
 'ст. 143–178 НК РФ'),
('TAX_REGIME', 3, 'Глава 25 НК РФ — Налог на прибыль организаций',
 'https://www.consultant.ru/document/cons_doc_LAW_28165/0438a065e17ec5e4dc52d2b00807a64f8e72e4b5/',
 'ст. 246–333 НК РФ'),
('TAX_REGIME', 3, 'Глава 23 НК РФ — НДФЛ (для ИП на ОСНО)',
 'https://www.consultant.ru/document/cons_doc_LAW_28165/6e2d3841e05f696fe089c1a893cc151c87b57795/',
 'ст. 207–233 НК РФ');

-- ПСН (id=4)
INSERT INTO legal_references (entity_type, entity_id, title, url, article) VALUES
('TAX_REGIME', 4, 'Глава 26.5 НК РФ — Патентная система налогообложения',
 'https://www.consultant.ru/document/cons_doc_LAW_28165/6e24430e66d65ce93e3dc8ef0ed4b6c00d7d1e45/',
 'ст. 346.43–346.53 НК РФ'),
('TAX_REGIME', 4, 'Виды предпринимательской деятельности для ПСН',
 'https://www.consultant.ru/document/cons_doc_LAW_28165/6e24430e66d65ce93e3dc8ef0ed4b6c00d7d1e45/',
 'ст. 346.43 п. 2 НК РФ');

-- НПД (id=5)
INSERT INTO legal_references (entity_type, entity_id, title, url, article) VALUES
('TAX_REGIME', 5, 'Федеральный закон №422-ФЗ от 27.11.2018',
 'https://www.consultant.ru/document/cons_doc_LAW_311977/',
 'ФЗ №422-ФЗ'),
('TAX_REGIME', 5, 'Порядок постановки на учёт как самозанятый',
 'https://www.consultant.ru/document/cons_doc_LAW_311977/f54c3240c6fc5b1ec0dbe3c7a71ddce7d6ee59de/',
 'ст. 5 ФЗ №422-ФЗ'),
('TAX_REGIME', 5, 'Налоговые ставки НПД: 4% и 6%',
 'https://www.consultant.ru/document/cons_doc_LAW_311977/9d8ba60e3413a48c38728bfafdc763ed33c75445/',
 'ст. 10 ФЗ №422-ФЗ');

-- ── Сценарии госзакупок (entity_type = 'PROCUREMENT') ─────────

-- 44-ФЗ общий порядок (id=1)
INSERT INTO legal_references (entity_type, entity_id, title, url, article) VALUES
('PROCUREMENT', 1, 'Федеральный закон №44-ФЗ от 05.04.2013',
 'https://www.consultant.ru/document/cons_doc_LAW_144624/',
 '44-ФЗ'),
('PROCUREMENT', 1, 'Требования к участникам закупки',
 'https://www.consultant.ru/document/cons_doc_LAW_144624/5e5c0d5ceb10857e79be5bfbb2b9c8cccb158547/',
 'ст. 31 44-ФЗ'),
('PROCUREMENT', 1, 'Обеспечение заявки на участие',
 'https://www.consultant.ru/document/cons_doc_LAW_144624/6e9b57b25b526b9b0614479a54a1608578cca616/',
 'ст. 44 44-ФЗ'),
('PROCUREMENT', 1, 'Содержание заявки участника',
 'https://www.consultant.ru/document/cons_doc_LAW_144624/3a7a04d1ddf9186a9b3c77c3e2e2c5e01da39e34/',
 'ст. 43 44-ФЗ');

-- 44-ФЗ для МСП (id=2)
INSERT INTO legal_references (entity_type, entity_id, title, url, article) VALUES
('PROCUREMENT', 2, 'Закупки у субъектов МСП по 44-ФЗ',
 'https://www.consultant.ru/document/cons_doc_LAW_144624/a73a0d6c6aff265f773dcee4b5e1af4064e6e60a/',
 'ст. 30 44-ФЗ'),
('PROCUREMENT', 2, 'Критерии отнесения к субъектам МСП',
 'https://www.consultant.ru/document/cons_doc_LAW_52144/08b3ecbcdc9a5fbe28e9b4cd5a2b6e3b52125cb9/',
 'ст. 4 ФЗ №209-ФЗ'),
('PROCUREMENT', 2, 'Реестр субъектов МСП (ФНС)',
 'https://rmsp.nalog.ru/',
 'rmsp.nalog.ru');

-- 223-ФЗ для МСП (id=3)
INSERT INTO legal_references (entity_type, entity_id, title, url, article) VALUES
('PROCUREMENT', 3, 'Федеральный закон №223-ФЗ от 18.07.2011',
 'https://www.consultant.ru/document/cons_doc_LAW_116964/',
 '223-ФЗ'),
('PROCUREMENT', 3, 'Закупки у субъектов МСП по 223-ФЗ',
 'https://www.consultant.ru/document/cons_doc_LAW_116964/1d5cd2a4aae3dfcb2e40ce3e7ed8d3ea14993984/',
 'ст. 3.4 223-ФЗ'),
('PROCUREMENT', 3, 'Постановление Правительства РФ №1352 — закупки у МСП',
 'https://www.consultant.ru/document/cons_doc_LAW_173678/',
 'ПП РФ №1352');
