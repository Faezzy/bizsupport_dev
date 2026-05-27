package ru.bizsupport.entity;

/**
 * Тип сущности — используется в user_favorites и legal_references
 * для полиморфной привязки к разным таблицам.
 */
public enum EntityType {
    TAX_REGIME("Налоговый режим"),
    PROCUREMENT("Сценарий закупки"),
    RISK("Карточка риска"),
    TENDER("Тендер");
    private final String displayName;
    EntityType(String d) { this.displayName = d; }
    public String getDisplayName() { return displayName; }
}
