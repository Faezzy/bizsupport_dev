package ru.bizsupport.entity;

public enum TenderLawType {
    FZ_44("44-ФЗ"),
    FZ_223("223-ФЗ"),
    COMMERCIAL("Коммерческая");

    private final String displayName;
    TenderLawType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
