-- ============================================================
-- V1__init_schema.sql
-- Начальная схема БД: BizSupport
-- ============================================================

-- Пользователи
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255),
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Профиль компании
CREATE TABLE company_profiles (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company_name    VARCHAR(255) NOT NULL,
    company_type    VARCHAR(10)  NOT NULL, -- IP / OOO
    inn             VARCHAR(12),
    ogrn            VARCHAR(15),
    industry        VARCHAR(255),
    employees_count INT,
    annual_revenue  NUMERIC(15,2),
    is_msp          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_company_user UNIQUE (user_id)
);

-- Налоговые режимы (справочник)
CREATE TABLE tax_regimes (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE, -- USN_6, USN_15, OSNO, PSN, NPD
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    conditions  TEXT,        -- JSON-строка с условиями применения
    nk_ref      VARCHAR(255) -- ссылка на статью НК РФ
);

-- Связь профиль ↔ режим налогообложения
CREATE TABLE company_tax_regimes (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT      NOT NULL REFERENCES company_profiles(id) ON DELETE CASCADE,
    tax_regime_id   BIGINT      NOT NULL REFERENCES tax_regimes(id),
    is_current      BOOLEAN     NOT NULL DEFAULT TRUE,
    applied_since   DATE,
    CONSTRAINT uq_company_regime UNIQUE (company_id, tax_regime_id)
);

-- Налоговые обязательства (список налогов для режима)
CREATE TABLE tax_obligations (
    id              BIGSERIAL PRIMARY KEY,
    tax_regime_id   BIGINT       NOT NULL REFERENCES tax_regimes(id) ON DELETE CASCADE,
    tax_name        VARCHAR(255) NOT NULL,
    rate            VARCHAR(50),  -- напр. "6%" или "15% от разницы"
    description     TEXT,
    nk_ref          VARCHAR(255),
    fns_service_url VARCHAR(500)
);

-- Дедлайны / налоговый календарь
CREATE TABLE deadlines (
    id              BIGSERIAL PRIMARY KEY,
    tax_regime_id   BIGINT       REFERENCES tax_regimes(id),
    company_id      BIGINT       REFERENCES company_profiles(id),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    due_date        DATE         NOT NULL,
    repeat_rule     VARCHAR(50),  -- ANNUAL, QUARTERLY, MONTHLY
    is_custom       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Уведомления
CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    deadline_id     BIGINT       REFERENCES deadlines(id),
    title           VARCHAR(255) NOT NULL,
    message         TEXT,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    send_at         TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Сценарии госзакупок (справочник)
CREATE TABLE procurement_scenarios (
    id          BIGSERIAL PRIMARY KEY,
    law_type    VARCHAR(10)  NOT NULL, -- FZ_44 / FZ_223
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    msp_only    BOOLEAN      NOT NULL DEFAULT FALSE,
    amount_min  NUMERIC(15,2),
    amount_max  NUMERIC(15,2)
);

-- Чек-листы
CREATE TABLE checklists (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       REFERENCES users(id) ON DELETE CASCADE,
    scenario_id         BIGINT       REFERENCES procurement_scenarios(id),
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    is_template         BOOLEAN      NOT NULL DEFAULT FALSE,
    is_completed        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Шаги чек-листа
CREATE TABLE checklist_steps (
    id              BIGSERIAL PRIMARY KEY,
    checklist_id    BIGINT       NOT NULL REFERENCES checklists(id) ON DELETE CASCADE,
    step_order      INT          NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    hint            TEXT,
    is_completed    BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at    TIMESTAMP
);

-- Карточки рисков
CREATE TABLE risk_cards (
    id              BIGSERIAL PRIMARY KEY,
    scenario_id     BIGINT       REFERENCES procurement_scenarios(id),
    title           VARCHAR(255) NOT NULL,
    risk_type       VARCHAR(50), -- FINANCIAL, LEGAL, PROCEDURAL
    description     TEXT,
    consequence     TEXT,
    recommendation  TEXT
);

-- Правовые ссылки
CREATE TABLE legal_references (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50)  NOT NULL, -- TAX_REGIME, PROCUREMENT, RISK
    entity_id   BIGINT       NOT NULL,
    title       VARCHAR(255) NOT NULL,
    url         VARCHAR(500),
    article     VARCHAR(100)
);

-- Избранные статьи пользователя
CREATE TABLE user_favorites (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       BIGINT      NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_favorite UNIQUE (user_id, entity_type, entity_id)
);

-- ============================================================
-- Начальные данные: налоговые режимы
-- ============================================================
INSERT INTO tax_regimes (code, name, description, conditions, nk_ref) VALUES
('USN_6',  'УСН «Доходы» (6%)',
 'Упрощённая система налогообложения. Объект — доходы. Ставка 6%.',
 '{"max_revenue":265800000,"max_employees":130,"allowed_types":["IP","OOO"]}',
 'НК РФ Глава 26.2'),

('USN_15', 'УСН «Доходы минус расходы» (15%)',
 'Упрощённая система налогообложения. Объект — доходы за вычетом расходов. Ставка 15%.',
 '{"max_revenue":265800000,"max_employees":130,"allowed_types":["IP","OOO"]}',
 'НК РФ Глава 26.2'),

('OSNO',   'ОСНО — Общая система',
 'Общая система налогообложения. НДС + налог на прибыль (для ООО) или НДФЛ (для ИП).',
 '{"allowed_types":["IP","OOO"]}',
 'НК РФ'),

('PSN',    'ПСН — Патентная система',
 'Патентная система. Покупка патента на определённый вид деятельности.',
 '{"max_revenue":60000000,"max_employees":15,"allowed_types":["IP"]}',
 'НК РФ Глава 26.5'),

('NPD',    'НПД — Налог на профессиональный доход',
 'Для самозанятых и ИП без наёмных сотрудников. Ставка 4% (физлица) или 6% (юрлица).',
 '{"max_revenue":2400000,"max_employees":0,"allowed_types":["IP"]}',
 'Федеральный закон №422-ФЗ от 27.11.2018');

-- Налоговые обязательства
INSERT INTO tax_obligations (tax_regime_id, tax_name, rate, description, nk_ref, fns_service_url)
SELECT id, 'Единый налог УСН', '6% от доходов',
       'Уплачивается ежеквартально авансовыми платежами, итог — по году.',
       'ст. 346.21 НК РФ', 'https://service.nalog.ru/usn.do'
FROM tax_regimes WHERE code = 'USN_6';

INSERT INTO tax_obligations (tax_regime_id, tax_name, rate, description, nk_ref, fns_service_url)
SELECT id, 'Страховые взносы ИП за себя', 'Фиксированная сумма + 1% с дохода > 300 000 руб.',
       'Обязательные взносы на ОПС и ОМС.',
       'ст. 430 НК РФ', 'https://www.nalog.gov.ru/rn77/taxation/insprem/'
FROM tax_regimes WHERE code = 'USN_6';

INSERT INTO tax_obligations (tax_regime_id, tax_name, rate, description, nk_ref, fns_service_url)
SELECT id, 'Единый налог УСН', '15% от (доходы − расходы), мин. 1% от доходов',
       'Если расходы велики — выгоднее чем УСН 6%.',
       'ст. 346.18 НК РФ', 'https://service.nalog.ru/usn.do'
FROM tax_regimes WHERE code = 'USN_15';

INSERT INTO tax_obligations (tax_regime_id, tax_name, rate, description, nk_ref, fns_service_url)
SELECT id, 'НДС', '20% (10% на ряд товаров, 0% на экспорт)',
       'Налог на добавленную стоимость. Декларация ежеквартально.',
       'Глава 21 НК РФ', 'https://www.nalog.gov.ru/rn77/taxation/taxes/nds/'
FROM tax_regimes WHERE code = 'OSNO';

INSERT INTO tax_obligations (tax_regime_id, tax_name, rate, description, nk_ref, fns_service_url)
SELECT id, 'НДФЛ (для ИП на ОСНО)', '13% / 15% от прибыли',
       'Налог на доходы физических лиц. Декларация 3-НДФЛ раз в год.',
       'Глава 23 НК РФ', 'https://www.nalog.gov.ru/rn77/taxation/taxes/ndfl/'
FROM tax_regimes WHERE code = 'OSNO';

-- Дедлайны (шаблонные, без привязки к компании)
INSERT INTO deadlines (tax_regime_id, title, description, due_date, repeat_rule, is_custom)
SELECT id, 'Сдача декларации УСН (ИП)', 'Годовая декларация по УСН для ИП', '2025-04-30', 'ANNUAL', FALSE
FROM tax_regimes WHERE code IN ('USN_6', 'USN_15')
LIMIT 1;

INSERT INTO deadlines (tax_regime_id, title, description, due_date, repeat_rule, is_custom)
SELECT id, 'Авансовый платёж УСН Q1', 'Авансовый платёж по УСН за 1 квартал', '2025-04-28', 'QUARTERLY', FALSE
FROM tax_regimes WHERE code IN ('USN_6', 'USN_15')
LIMIT 1;

INSERT INTO deadlines (tax_regime_id, title, description, due_date, repeat_rule, is_custom)
SELECT id, 'Уплата фиксированных взносов ИП', 'Страховые взносы ИП за себя за год', '2025-12-31', 'ANNUAL', FALSE
FROM tax_regimes WHERE code = 'USN_6';

-- Сценарии госзакупок
INSERT INTO procurement_scenarios (law_type, title, description, msp_only, amount_min, amount_max) VALUES
('FZ_44', 'Участие в закупке по 44-ФЗ (общий порядок)',
 'Стандартный порядок участия в государственных закупках по Федеральному закону №44-ФЗ.',
 FALSE, NULL, NULL),

('FZ_44', 'Закупка среди МСП по 44-ФЗ',
 'Участие в закупках, проводимых исключительно среди субъектов МСП. Доля закупок у МСП — не менее 25% от СГОЗ.',
 TRUE, NULL, 20000000),

('FZ_223', 'Участие в закупке по 223-ФЗ (для МСП)',
 'Участие в закупках госкомпаний. Доля закупок у МСП — не менее 25% от совокупного годового объёма.',
 TRUE, NULL, NULL);

-- Чек-лист 44-ФЗ (шаблон)
INSERT INTO checklists (scenario_id, title, description, is_template)
SELECT id, 'Чек-лист: участие в закупке по 44-ФЗ',
       'Пошаговая инструкция от регистрации до подписания контракта по 44-ФЗ', TRUE
FROM procurement_scenarios WHERE law_type = 'FZ_44' AND msp_only = FALSE LIMIT 1;

INSERT INTO checklist_steps (checklist_id, step_order, title, description, hint) VALUES
(1, 1, 'Получить усиленную квалифицированную ЭЦП',
 'Оформить УКЭП в аккредитованном удостоверяющем центре (для руководителя и/или уполномоченного лица).',
 'Перечень аккредитованных УЦ: https://www.gosuslugi.ru/pgu/cms/content/view/id/595670'),

(1, 2, 'Зарегистрироваться в ЕСИА (Госуслуги)',
 'Создать подтверждённую учётную запись на портале Госуслуг.',
 'Требуется для регистрации в ЕИС'),

(1, 3, 'Зарегистрироваться в ЕИС (zakupki.gov.ru)',
 'Пройти регистрацию в Единой информационной системе в сфере закупок.',
 'После регистрации в ЕИС автоматически появляетесь на 8 федеральных ЭТП'),

(1, 4, 'Открыть спецсчёт в уполномоченном банке',
 'Для обеспечения заявки на участие в электронном аукционе необходим спецсчёт.',
 'Перечень уполномоченных банков утверждён Правительством РФ. Среди них: Сбербанк, ВТБ, Альфа-Банк.'),

(1, 5, 'Найти подходящую закупку',
 'Поиск закупок на ЭТП или в ЕИС по коду ОКПД2 и ключевым словам.',
 'Используйте фильтр "Только для МСП" если ваша компания внесена в реестр МСП'),

(1, 6, 'Изучить документацию о закупке',
 'Внимательно прочитать техническое задание, проект контракта, требования к участникам.',
 'Обратите внимание на требования к документам: лицензии, допуски, опыт.'),

(1, 7, 'Подать заявку на участие',
 'Сформировать и подписать УКЭП заявку в личном кабинете ЭТП.',
 'Дедлайн подачи заявки — обычно за 7 дней до аукциона (для малых закупок — 1 день)'),

(1, 8, 'Участвовать в торгах',
 'В назначенное время принять участие в электронном аукционе — снижать цену шагами от 0.5% до 5%.',
 'Держите запасной интернет-канал: обрыв связи во время торгов не является основанием для отмены'),

(1, 9, 'Подписать контракт',
 'Если победили — подписать контракт УКЭП в течение 5 рабочих дней.',
 'Предоставьте обеспечение исполнения контракта (банковская гарантия или денежный залог)');

-- Карточки рисков
INSERT INTO risk_cards (scenario_id, title, risk_type, description, consequence, recommendation) VALUES
(1, 'Недостаточное обеспечение заявки', 'FINANCIAL',
 'Средств на спецсчёте недостаточно для блокировки суммы обеспечения заявки.',
 'Отказ в приёме заявки. Пропуск торгов.',
 'Заблаговременно пополнить спецсчёт. Сумма блокировки — до 5% от НМЦК.'),

(1, 'Ошибки в документах заявки', 'PROCEDURAL',
 'Неверно оформленные или неполные документы в составе заявки.',
 'Отклонение заявки на этапе рассмотрения.',
 'Использовать чек-лист документов. Изучить требования 44-ФЗ к составу заявки (ст. 43).'),

(1, 'Нарушение срока подписания контракта', 'LEGAL',
 'Победитель не подписал контракт в установленный срок (5 рабочих дней).',
 'Признание победителя уклонившимся. Внесение в РНП. Потеря обеспечения заявки.',
 'Подписывать контракт сразу после получения уведомления о победе. Проверить УКЭП.'),

(2, 'Несоответствие требованиям МСП', 'LEGAL',
 'Компания не включена в реестр МСП или данные не актуальны.',
 'Отклонение заявки на закупках для МСП.',
 'Проверить наличие в реестре МСП: https://rmsp.nalog.ru/. Обновить данные при необходимости.');
