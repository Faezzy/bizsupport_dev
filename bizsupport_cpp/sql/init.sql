-- BizSupport C++ — Initial Database Schema and Seed Data
-- Combined from Flyway migrations V1, V2, V3

-- ============================================================
-- SCHEMA
-- ============================================================

-- Users
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255),
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Company profiles
CREATE TABLE IF NOT EXISTS company_profiles (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company_name    VARCHAR(255) NOT NULL,
    company_type    VARCHAR(10)  NOT NULL,
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

-- Tax regimes
CREATE TABLE IF NOT EXISTS tax_regimes (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    conditions  TEXT,
    nk_ref      VARCHAR(255)
);

-- Company-tax regime link
CREATE TABLE IF NOT EXISTS company_tax_regimes (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT      NOT NULL REFERENCES company_profiles(id) ON DELETE CASCADE,
    tax_regime_id   BIGINT      NOT NULL REFERENCES tax_regimes(id),
    is_current      BOOLEAN     NOT NULL DEFAULT TRUE,
    applied_since   DATE,
    CONSTRAINT uq_company_regime UNIQUE (company_id, tax_regime_id)
);

-- Tax obligations
CREATE TABLE IF NOT EXISTS tax_obligations (
    id              BIGSERIAL PRIMARY KEY,
    tax_regime_id   BIGINT       NOT NULL REFERENCES tax_regimes(id) ON DELETE CASCADE,
    tax_name        VARCHAR(255) NOT NULL,
    rate            VARCHAR(50),
    description     TEXT,
    nk_ref          VARCHAR(255),
    fns_service_url VARCHAR(500)
);

-- Deadlines
CREATE TABLE IF NOT EXISTS deadlines (
    id              BIGSERIAL PRIMARY KEY,
    tax_regime_id   BIGINT       REFERENCES tax_regimes(id),
    company_id      BIGINT       REFERENCES company_profiles(id),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    due_date        DATE         NOT NULL,
    repeat_rule     VARCHAR(50),
    is_custom       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    deadline_id     BIGINT       REFERENCES deadlines(id),
    title           VARCHAR(255) NOT NULL,
    message         TEXT,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    send_at         TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Procurement scenarios
CREATE TABLE IF NOT EXISTS procurement_scenarios (
    id          BIGSERIAL PRIMARY KEY,
    law_type    VARCHAR(10)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    msp_only    BOOLEAN      NOT NULL DEFAULT FALSE,
    amount_min  NUMERIC(15,2),
    amount_max  NUMERIC(15,2)
);

-- Checklists
CREATE TABLE IF NOT EXISTS checklists (
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

-- Checklist steps
CREATE TABLE IF NOT EXISTS checklist_steps (
    id              BIGSERIAL PRIMARY KEY,
    checklist_id    BIGINT       NOT NULL REFERENCES checklists(id) ON DELETE CASCADE,
    step_order      INT          NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    hint            TEXT,
    is_completed    BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at    TIMESTAMP
);

-- Risk cards
CREATE TABLE IF NOT EXISTS risk_cards (
    id              BIGSERIAL PRIMARY KEY,
    scenario_id     BIGINT       REFERENCES procurement_scenarios(id),
    title           VARCHAR(255) NOT NULL,
    risk_type       VARCHAR(50),
    description     TEXT,
    consequence     TEXT,
    recommendation  TEXT
);

-- Legal references
CREATE TABLE IF NOT EXISTS legal_references (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    title       VARCHAR(255) NOT NULL,
    url         VARCHAR(500),
    article     VARCHAR(100)
);

-- User favorites
CREATE TABLE IF NOT EXISTS user_favorites (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       BIGINT      NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_favorite UNIQUE (user_id, entity_type, entity_id)
);

-- Tenders
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

CREATE INDEX IF NOT EXISTS idx_tender_law      ON tenders(law_type);
CREATE INDEX IF NOT EXISTS idx_tender_status   ON tenders(status);
CREATE INDEX IF NOT EXISTS idx_tender_region   ON tenders(region);
CREATE INDEX IF NOT EXISTS idx_tender_deadline ON tenders(submission_deadline);

-- ============================================================
-- SEED DATA
-- ============================================================

-- ------------------------------------------------------------
-- 1. Admin user
-- ------------------------------------------------------------
INSERT INTO users (email, password, full_name, role)
VALUES (
    'admin@bizsupport.ru',
    '$pbkdf2$a1b2c3d4e5f6a7b8a1b2c3d4e5f6a7b8$e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    'Администратор',
    'ADMIN'
) ON CONFLICT (email) DO NOTHING;

-- ------------------------------------------------------------
-- 2. Tax regimes
-- ------------------------------------------------------------
INSERT INTO tax_regimes (code, name, description, conditions, nk_ref) VALUES
('USN_6', 'УСН «Доходы» 6%',
 'Упрощённая система налогообложения с объектом «доходы». Ставка 6% от всех поступлений. Подходит для бизнеса с небольшими расходами (услуги, IT, консалтинг).',
 'Доход до 265,8 млн руб./год; до 130 сотрудников; остаточная стоимость ОС до 150 млн руб.',
 'Глава 26.2 НК РФ, ст. 346.20'),

('USN_15', 'УСН «Доходы минус расходы» 15%',
 'Упрощённая система налогообложения с объектом «доходы минус расходы». Ставка 15% от прибыли. Выгодна при доле расходов более 60%.',
 'Доход до 265,8 млн руб./год; до 130 сотрудников; остаточная стоимость ОС до 150 млн руб.',
 'Глава 26.2 НК РФ, ст. 346.20'),

('OSNO', 'Общая система налогообложения (ОСНО)',
 'Основная система налогообложения. Предусматривает уплату НДС (20%), налога на прибыль (20%) и налога на имущество. Обязательна для крупного бизнеса и работы с НДС-плательщиками.',
 'Без ограничений. Применяется по умолчанию при регистрации.',
 'НК РФ, главы 21, 25, 30'),

('PSN', 'Патентная система налогообложения (ПСН)',
 'Патент для ИП. Фиксированная стоимость патента зависит от вида деятельности и региона. Не требует сдачи декларации.',
 'Только ИП; до 15 сотрудников; доход до 60 млн руб./год; ограниченный перечень видов деятельности.',
 'Глава 26.5 НК РФ'),

('NPD', 'Налог на профессиональный доход (НПД)',
 'Специальный режим для самозанятых и ИП без сотрудников. Ставки: 4% при работе с физлицами, 6% — с юрлицами. Регистрация через приложение «Мой налог».',
 'Доход до 2,4 млн руб./год; нет наёмных работников; ограниченный перечень деятельности.',
 'Федеральный закон №422-ФЗ от 27.11.2018');

-- ------------------------------------------------------------
-- 3. Tax obligations
-- ------------------------------------------------------------
-- USN 6% (regime_id = 1)
INSERT INTO tax_obligations (tax_regime_id, tax_name, rate, description, nk_ref, fns_service_url) VALUES
(1, 'Единый налог УСН (доходы)', '6%',
 'Налог уплачивается ежеквартально авансовыми платежами. Можно уменьшить на страховые взносы (ИП без работников — на 100%, с работниками — до 50%).',
 'ст. 346.21 НК РФ',
 'https://www.nalog.gov.ru/rn77/taxation/taxes/usn/'),
(1, 'Страховые взносы ИП (фиксированные)', '49 500 руб. (2025)',
 'Фиксированные взносы на ОПС и ОМС, уплачиваются до 31 декабря текущего года.',
 'ст. 430 НК РФ',
 'https://www.nalog.gov.ru/rn77/taxation/taxes/usn/'),
(1, 'Страховые взносы ИП (1% сверх 300 тыс.)', '1% от дохода свыше 300 000 руб.',
 'Дополнительный взнос на ОПС с суммы дохода, превышающей 300 000 руб. Срок — до 1 июля следующего года.',
 'ст. 430 НК РФ',
 'https://www.nalog.gov.ru/rn77/taxation/taxes/usn/');

-- USN 15% (regime_id = 2)
INSERT INTO tax_obligations (tax_regime_id, tax_name, rate, description, nk_ref, fns_service_url) VALUES
(2, 'Единый налог УСН (доходы минус расходы)', '15%',
 'Налог уплачивается ежеквартально. Минимальный налог — 1% от доходов, если расчётный налог меньше.',
 'ст. 346.21 НК РФ',
 'https://www.nalog.gov.ru/rn77/taxation/taxes/usn/'),
(2, 'Страховые взносы ИП (фиксированные)', '49 500 руб. (2025)',
 'Фиксированные взносы на ОПС и ОМС.',
 'ст. 430 НК РФ',
 'https://www.nalog.gov.ru/rn77/taxation/taxes/usn/'),
(2, 'Страховые взносы ИП (1% сверх 300 тыс.)', '1% от (доходы − расходы) свыше 300 000 руб.',
 'Базой для расчёта 1% является разница между доходами и расходами.',
 'ст. 430 НК РФ',
 'https://www.nalog.gov.ru/rn77/taxation/taxes/usn/');

-- OSNO (regime_id = 3)
INSERT INTO tax_obligations (tax_regime_id, tax_name, rate, description, nk_ref, fns_service_url) VALUES
(3, 'НДС', '20% (10%, 0%)',
 'Налог на добавленную стоимость. Основная ставка 20%. Льготные ставки: 10% — продовольствие, детские товары; 0% — экспорт.',
 'Глава 21 НК РФ',
 'https://www.nalog.gov.ru/rn77/taxation/taxes/nds/'),
(3, 'Налог на прибыль', '20%',
 'Федеральная часть — 3%, региональная — 17%. Авансовые платежи ежемесячно или ежеквартально.',
 'Глава 25 НК РФ',
 'https://www.nalog.gov.ru/rn77/taxation/taxes/profitul/'),
(3, 'Налог на имущество организаций', 'до 2,2%',
 'Уплачивается с кадастровой или балансовой стоимости недвижимости.',
 'Глава 30 НК РФ',
 'https://www.nalog.gov.ru/rn77/taxation/taxes/imuchorg/'),
(3, 'Страховые взносы за сотрудников', '30% (до предела) + 15,1%',
 'ОПС — 22%, ОМС — 5,1%, ФСС — 2,9%. Для МСП: 15% с суммы свыше МРОТ.',
 'Глава 34 НК РФ',
 'https://www.nalog.gov.ru/rn77/taxation/taxes/insurance/');

-- PSN (regime_id = 4)
INSERT INTO tax_obligations (tax_regime_id, tax_name, rate, description, nk_ref, fns_service_url) VALUES
(4, 'Стоимость патента', '6% от потенциального дохода',
 'Фиксированная сумма, зависит от вида деятельности и региона. Рассчитывается на сайте ФНС.',
 'ст. 346.51 НК РФ',
 'https://patent.nalog.ru/'),
(4, 'Страховые взносы ИП (фиксированные)', '49 500 руб. (2025)',
 'Можно уменьшить стоимость патента на сумму уплаченных взносов.',
 'ст. 430 НК РФ',
 'https://www.nalog.gov.ru/rn77/taxation/taxes/patent/');

-- NPD (regime_id = 5)
INSERT INTO tax_obligations (tax_regime_id, tax_name, rate, description, nk_ref, fns_service_url) VALUES
(5, 'НПД (от физлиц)', '4%',
 'Ставка при получении дохода от физических лиц.',
 'ст. 10 422-ФЗ',
 'https://npd.nalog.ru/'),
(5, 'НПД (от юрлиц и ИП)', '6%',
 'Ставка при получении дохода от юридических лиц и ИП.',
 'ст. 10 422-ФЗ',
 'https://npd.nalog.ru/'),
(5, 'Налоговый вычет', 'до 10 000 руб.',
 'Единоразовый вычет, уменьшающий ставку: с 4% до 3% (физлица) и с 6% до 4% (юрлица). Расходуется автоматически.',
 'ст. 12 422-ФЗ',
 'https://npd.nalog.ru/');

-- ------------------------------------------------------------
-- 4. Template deadlines
-- ------------------------------------------------------------
INSERT INTO deadlines (tax_regime_id, title, description, due_date, repeat_rule, is_custom) VALUES
(1, 'Авансовый платёж УСН за I квартал',  'Уплата авансового платежа по УСН за I квартал.',  '2025-04-28', 'QUARTERLY', FALSE),
(1, 'Авансовый платёж УСН за полугодие',  'Уплата авансового платежа по УСН за полугодие.',  '2025-07-28', 'QUARTERLY', FALSE),
(1, 'Авансовый платёж УСН за 9 месяцев',  'Уплата авансового платежа по УСН за 9 месяцев.',  '2025-10-28', 'QUARTERLY', FALSE),
(1, 'Декларация и налог УСН за год',       'Подача декларации и уплата налога УСН за год (ИП — до 25 апреля, организации — до 25 марта).', '2026-04-25', 'YEARLY', FALSE),
(1, 'Фиксированные взносы ИП',            'Уплата фиксированных страховых взносов ИП за текущий год.', '2025-12-31', 'YEARLY', FALSE),
(1, '1% взнос ИП сверх 300 тыс.',         'Уплата 1% взноса на ОПС с дохода свыше 300 000 руб.',       '2026-07-01', 'YEARLY', FALSE),

(2, 'Авансовый платёж УСН за I квартал',  'Уплата авансового платежа по УСН (доходы−расходы) за I квартал.', '2025-04-28', 'QUARTERLY', FALSE),
(2, 'Авансовый платёж УСН за полугодие',  'Уплата авансового платежа по УСН (доходы−расходы) за полугодие.', '2025-07-28', 'QUARTERLY', FALSE),
(2, 'Авансовый платёж УСН за 9 месяцев',  'Уплата авансового платежа по УСН (доходы−расходы) за 9 месяцев.', '2025-10-28', 'QUARTERLY', FALSE),
(2, 'Декларация и налог УСН за год',       'Подача декларации и уплата налога УСН (доходы−расходы) за год.',  '2026-04-25', 'YEARLY', FALSE),

(3, 'Декларация по НДС за I квартал',     'Подача декларации по НДС за I квартал. Только в электронном виде.', '2025-04-25', 'QUARTERLY', FALSE),
(3, 'Декларация по НДС за II квартал',    'Подача декларации по НДС за II квартал.',                          '2025-07-25', 'QUARTERLY', FALSE),
(3, 'Декларация по НДС за III квартал',   'Подача декларации по НДС за III квартал.',                         '2025-10-27', 'QUARTERLY', FALSE),
(3, 'Декларация по НДС за IV квартал',    'Подача декларации по НДС за IV квартал.',                          '2026-01-26', 'QUARTERLY', FALSE),
(3, 'Декларация по налогу на прибыль',     'Подача годовой декларации по налогу на прибыль.',                  '2026-03-28', 'YEARLY', FALSE),

(4, 'Оплата патента (до 6 мес.)',          'Полная оплата патента сроком до 6 месяцев — до окончания срока действия.', '2025-06-30', 'NONE', FALSE),
(4, 'Оплата патента — 1/3 (свыше 6 мес.)', 'Первая треть стоимости патента сроком свыше 6 месяцев — в течение 90 дней.', '2025-03-31', 'NONE', FALSE),
(4, 'Оплата патента — 2/3 (свыше 6 мес.)', 'Оставшиеся 2/3 стоимости патента — до окончания срока действия.',           '2025-12-31', 'NONE', FALSE),

(5, 'Уплата НПД за текущий месяц',        'Налог рассчитывается автоматически в приложении «Мой налог». Срок уплаты — 28-е число следующего месяца.', '2025-02-28', 'MONTHLY', FALSE);

-- ------------------------------------------------------------
-- 5. Procurement scenarios
-- ------------------------------------------------------------
INSERT INTO procurement_scenarios (law_type, title, description, msp_only, amount_min, amount_max) VALUES
('FZ_44', 'Участие в закупках по 44-ФЗ (общий порядок)',
 'Участие в государственных и муниципальных закупках по Федеральному закону №44-ФЗ «О контрактной системе». Включает электронные аукционы, конкурсы, запросы котировок.',
 FALSE, 0.00, NULL),

('FZ_44', 'Закупки для МСП по 44-ФЗ',
 'Закупки, в которых участие ограничено субъектами малого и среднего предпринимательства (МСП). Заказчики обязаны закупать у МСП не менее 25% годового объёма.',
 TRUE, 0.00, 20000000.00),

('FZ_223', 'Закупки по 223-ФЗ для МСП',
 'Участие в закупках компаний с госучастием по 223-ФЗ. Отдельные процедуры для МСП. Порядок определяется положением о закупках заказчика.',
 TRUE, 0.00, NULL);

-- ------------------------------------------------------------
-- 6. Checklist template for FZ_44 (scenario_id = 1)
-- ------------------------------------------------------------
INSERT INTO checklists (scenario_id, title, description, is_template, is_completed) VALUES
(1, 'Подготовка к участию в закупке по 44-ФЗ',
 'Пошаговый чек-лист для подготовки заявки на участие в государственной закупке.',
 TRUE, FALSE);

INSERT INTO checklist_steps (checklist_id, step_order, title, description, hint, is_completed) VALUES
(1, 1, 'Получить электронную подпись (ЭП)',
 'Оформите квалифицированную электронную подпись в аккредитованном удостоверяющем центре.',
 'ИП и руководители юрлиц получают ЭП бесплатно в ФНС. Доверенным лицам — в коммерческих УЦ.',
 FALSE),
(1, 2, 'Зарегистрироваться в ЕИС',
 'Зарегистрируйтесь в Единой информационной системе закупок (zakupki.gov.ru).',
 'Потребуется подтверждённая учётная запись на Госуслугах (ЕСИА).',
 FALSE),
(1, 3, 'Аккредитоваться на электронной площадке',
 'Пройдите аккредитацию на одной из 8 федеральных электронных площадок.',
 'Аккредитация через ЕИС действует на всех площадках. Срок — 1 рабочий день.',
 FALSE),
(1, 4, 'Открыть спецсчёт',
 'Откройте специальный счёт в одном из уполномоченных банков для обеспечения заявок.',
 'Список банков утверждён Правительством РФ. Спецсчёт можно открыть бесплатно.',
 FALSE),
(1, 5, 'Изучить документацию закупки',
 'Внимательно изучите извещение, техническое задание и проект контракта.',
 'Обратите внимание на требования к участникам (ст. 31 44-ФЗ), сроки, объём и условия оплаты.',
 FALSE),
(1, 6, 'Подготовить заявку',
 'Заполните формы заявки на электронной площадке. Приложите необходимые документы.',
 'Для электронного аукциона заявка состоит из двух частей. Проверьте комплектность.',
 FALSE),
(1, 7, 'Обеспечить заявку',
 'Перечислите сумму обеспечения заявки на спецсчёт или оформите банковскую гарантию.',
 'Размер обеспечения: от 0,5% до 5% от НМЦК (для МСП — до 2%).',
 FALSE),
(1, 8, 'Подать заявку',
 'Подпишите заявку электронной подписью и отправьте через электронную площадку.',
 'Подайте заявку заблаговременно — минимум за 1 день до окончания срока.',
 FALSE),
(1, 9, 'Подготовить обеспечение контракта',
 'Подготовьте обеспечение исполнения контракта (банковская гарантия или денежные средства).',
 'Размер: от 5% до 30% от цены контракта. Для МСП можно предоставить в уменьшенном размере.',
 FALSE);

-- ------------------------------------------------------------
-- 7. Risk cards (V1 — scenario 1)
-- ------------------------------------------------------------
INSERT INTO risk_cards (scenario_id, title, risk_type, description, consequence, recommendation) VALUES
(1, 'Недостаточное обеспечение заявки', 'FINANCIAL',
 'На спецсчёте недостаточно средств для обеспечения заявки на момент окончания срока подачи.',
 'Заявка будет отклонена оператором электронной площадки. Участие в закупке невозможно.',
 'Пополните спецсчёт минимум за 2 рабочих дня до окончания подачи. Учитывайте все активные заявки.'),

(1, 'Несоответствие требованиям ст. 31 44-ФЗ', 'LEGAL',
 'Участник не соответствует единым или дополнительным требованиям к участникам закупки.',
 'Отклонение заявки на этапе рассмотрения. Возможно включение в РНП при уклонении от контракта.',
 'Проверьте отсутствие задолженности по налогам, судимости у руководителя, наличие лицензий.'),

(1, 'Ошибки в заявке', 'PROCEDURAL',
 'Заявка содержит неполную или недостоверную информацию, отсутствуют обязательные документы.',
 'Отклонение заявки комиссией заказчика.',
 'Используйте чек-лист проверки заявки. Сверьте документы с требованиями извещения.'),

(1, 'Демпинг — аномально низкая цена', 'FINANCIAL',
 'Цена контракта снижена более чем на 25% от НМЦК.',
 'Заказчик потребует обоснование цены и/или увеличенное обеспечение исполнения контракта (в 1,5 раза).',
 'Подготовьте заранее обоснование (гарантийное письмо, коммерческие предложения, калькуляцию). Оцените рентабельность.');

-- ------------------------------------------------------------
-- 7b. Risk cards (V3 — scenarios 2 and 3)
-- ------------------------------------------------------------
INSERT INTO risk_cards (scenario_id, title, risk_type, description, consequence, recommendation) VALUES
(2, 'Отсутствие в реестре МСП', 'LEGAL',
 'Компания не внесена в Единый реестр МСП на момент подачи заявки.',
 'Заявка будет отклонена заказчиком.',
 'Проверьте включение в реестр на https://rmsp.nalog.ru/. Данные обновляются ежегодно 10 августа.'),

(2, 'Демпинг — аномально низкая цена', 'FINANCIAL',
 'Снижение цены более чем на 25% от НМЦК при закупке для МСП.',
 'Заказчик потребует обоснование или увеличенное обеспечение контракта (в 1,5 раза).',
 'Подготовьте калькуляцию себестоимости заранее. Не снижайте ниже порога рентабельности.'),

(2, 'Ограничение по сумме контракта', 'PROCEDURAL',
 'НМЦК закупки для МСП по 44-ФЗ ограничена 20 млн рублей.',
 'Закупки свыше 20 млн руб. проводятся в общем порядке.',
 'Проверяйте НМЦК перед подачей. Для крупных контрактов используйте общий порядок (сценарий 1).'),

(3, 'Нарушение срока оплаты по 223-ФЗ', 'FINANCIAL',
 'Заказчик нарушает установленный срок оплаты по договору.',
 'Задержка оплаты до 30 рабочих дней (для МСП — до 15). Кассовый разрыв.',
 'Включите в договор штрафные санкции за просрочку. Для МСП срок оплаты не более 15 р/д.'),

(3, 'Изменение условий договора заказчиком', 'LEGAL',
 'Заказчик по 223-ФЗ в одностороннем порядке меняет существенные условия договора.',
 'Увеличение объёма работ без пересмотра цены. Убытки подрядчика.',
 'Фиксируйте все изменения дополнительными соглашениями. Изучите типовое положение о закупках заказчика.'),

(3, 'Непрозрачность закупочных процедур', 'PROCEDURAL',
 'Заказчик по 223-ФЗ устанавливает субъективные критерии отбора.',
 'Отклонение заявки по неочевидным основаниям. Сложности с обжалованием.',
 'Подавайте запросы на разъяснение документации. При нарушениях — жалоба в ФАС (срок: 10 дней).');

-- ------------------------------------------------------------
-- 8. Legal references (V2)
-- ------------------------------------------------------------
INSERT INTO legal_references (entity_type, entity_id, title, url, article) VALUES
('TAX_REGIME', 1, 'НК РФ Глава 26.2 — Упрощённая система налогообложения', 'https://www.consultant.ru/document/cons_doc_LAW_28165/2398b3c006bc44e8e44caa2c4e17904a16e8157a/', 'Глава 26.2 НК РФ'),
('TAX_REGIME', 1, 'Ставки УСН «Доходы»', 'https://www.nalog.gov.ru/rn77/taxation/taxes/usn/', 'ст. 346.20 НК РФ'),
('TAX_REGIME', 2, 'НК РФ Глава 26.2 — УСН (доходы минус расходы)', 'https://www.consultant.ru/document/cons_doc_LAW_28165/2398b3c006bc44e8e44caa2c4e17904a16e8157a/', 'Глава 26.2 НК РФ'),
('TAX_REGIME', 2, 'Перечень расходов для УСН 15%', 'https://www.nalog.gov.ru/rn77/taxation/taxes/usn/', 'ст. 346.16 НК РФ'),
('TAX_REGIME', 3, 'НК РФ — Общая система налогообложения', 'https://www.consultant.ru/document/cons_doc_LAW_28165/', 'НК РФ'),
('TAX_REGIME', 3, 'НДС — Глава 21 НК РФ', 'https://www.consultant.ru/document/cons_doc_LAW_28165/6e08c4e3e83c70293de944a50096c3ad0fb03a08/', 'Глава 21 НК РФ'),
('TAX_REGIME', 4, 'НК РФ Глава 26.5 — Патентная система', 'https://www.consultant.ru/document/cons_doc_LAW_28165/acc274c26a4e783dd0a0a72e07a1e1a01e6b44fc/', 'Глава 26.5 НК РФ'),
('TAX_REGIME', 5, 'Федеральный закон №422-ФЗ — НПД', 'https://www.consultant.ru/document/cons_doc_LAW_311977/', '422-ФЗ от 27.11.2018'),
('PROCUREMENT', 1, 'Федеральный закон №44-ФЗ «О контрактной системе»', 'https://www.consultant.ru/document/cons_doc_LAW_144624/', '44-ФЗ'),
('PROCUREMENT', 1, 'Требования к участникам закупки', 'https://www.consultant.ru/document/cons_doc_LAW_144624/b843687e8ecd52ef12e4f27318afe34f05e3cfbe/', 'ст. 31 44-ФЗ'),
('PROCUREMENT', 2, 'Закупки у МСП по 44-ФЗ', 'https://www.consultant.ru/document/cons_doc_LAW_144624/', 'ст. 30 44-ФЗ'),
('PROCUREMENT', 3, 'Федеральный закон №223-ФЗ', 'https://www.consultant.ru/document/cons_doc_LAW_116964/', '223-ФЗ'),
('RISK', 1, 'Обеспечение заявки — ст. 44 44-ФЗ', 'https://www.consultant.ru/document/cons_doc_LAW_144624/', 'ст. 44 44-ФЗ'),
('RISK', 2, 'Требования к заявке — ст. 43 44-ФЗ', 'https://www.consultant.ru/document/cons_doc_LAW_144624/', 'ст. 43 44-ФЗ'),
('RISK', 3, 'Уклонение от контракта — ст. 104 44-ФЗ', 'https://www.consultant.ru/document/cons_doc_LAW_144624/', 'ст. 104 44-ФЗ'),
('RISK', 4, 'Реестр МСП', 'https://rmsp.nalog.ru/', 'ФЗ №209');
