package ru.bizsupport.service.provider;

import ru.bizsupport.entity.Tender;

import java.util.List;

/**
 * Абстракция источника данных о тендерах.
 *
 * Реализации:
 *   - MockTenderProvider     — статические демо-данные (для разработки и диплома)
 *   - EisTenderProvider      — интеграция с zakupki.gov.ru (TODO)
 *   - KonturTenderProvider   — Контур.Закупки API (платный) (TODO)
 *
 * Активный провайдер выбирается в application.properties:
 *   app.tenders.provider=mock
 */
public interface TenderProvider {

    /** Источник данных (mock, eis, kontur, ...) */
    String getName();

    /** Получить начальный набор тендеров (вызывается при старте, если БД пустая) */
    List<Tender> fetchInitial();

    /** Поддержка обновления — провайдер умеет дополнять данные? */
    default boolean supportsRefresh() { return false; }

    /** Дополнить актуальными тендерами. Вызывается по расписанию. */
    default List<Tender> fetchUpdates() { return List.of(); }
}
