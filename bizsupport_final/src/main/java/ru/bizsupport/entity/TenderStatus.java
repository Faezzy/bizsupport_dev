package ru.bizsupport.entity;

public enum TenderStatus {
    PUBLISHED("Подача заявок", "success"),
    UNDER_REVIEW("Рассмотрение заявок", "warning"),
    AUCTION("Торги", "info"),
    COMPLETED("Завершена", "muted"),
    CANCELLED("Отменена", "danger");

    private final String displayName;
    private final String badgeStyle;

    TenderStatus(String displayName, String badgeStyle) {
        this.displayName = displayName;
        this.badgeStyle = badgeStyle;
    }

    public String getDisplayName() { return displayName; }
    public String getBadgeStyle() { return badgeStyle; }
}
