-- ============================================================
-- V4__create_admin_user.sql
-- Администратор по умолчанию: admin@bizsupport.ru / admin123
-- Пароль: BCrypt hash от 'admin123'
-- ============================================================

INSERT INTO users (email, password, full_name, role, created_at, updated_at)
VALUES (
    'admin@bizsupport.ru',
    '$2b$10$Jy5llXjRSVObZ2VjoXY6K.DxNotS5223Y7Dfnl7bl94qRlDUis6AK',
    'Администратор',
    'ADMIN',
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;
