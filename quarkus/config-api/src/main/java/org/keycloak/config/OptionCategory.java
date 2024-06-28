package org.keycloak.config;

public enum OptionCategory {
    // ordered by name asc
    CACHE("Cache", 10, ConfigSupportLevel.SUPPORTED),
    CONFIG("Config", 15, ConfigSupportLevel.SUPPORTED),
    DATABASE("Database", 20, ConfigSupportLevel.SUPPORTED),
    TRANSACTION("Transaction",30, ConfigSupportLevel.SUPPORTED),
    FEATURE("Feature", 40, ConfigSupportLevel.SUPPORTED),
    HOSTNAME_V2("Hostname v2", 50, ConfigSupportLevel.SUPPORTED),
    HOSTNAME_V1("Hostname v1", 51, ConfigSupportLevel.DEPRECATED),
    HTTP("HTTP(S)", 60, ConfigSupportLevel.SUPPORTED),
    HEALTH("Health", 70, ConfigSupportLevel.SUPPORTED),
    MANAGEMENT("Management", 75, ConfigSupportLevel.SUPPORTED),
    METRICS("Metrics", 80, ConfigSupportLevel.SUPPORTED),
    PROXY("Proxy", 90, ConfigSupportLevel.SUPPORTED),
    VAULT("Vault", 100, ConfigSupportLevel.SUPPORTED),
    LOGGING("Logging", 110, ConfigSupportLevel.SUPPORTED),
    TRUSTSTORE("Truststore", 115, ConfigSupportLevel.SUPPORTED),
    SECURITY("Security", 120, ConfigSupportLevel.SUPPORTED),
    EXPORT("Export", 130, ConfigSupportLevel.SUPPORTED),
    IMPORT("Import", 140, ConfigSupportLevel.SUPPORTED),
    GENERAL("General", 999, ConfigSupportLevel.SUPPORTED);

    private final String heading;
    //Categories with a lower number are shown before groups with a higher number
    private final int order;
    private final ConfigSupportLevel supportLevel;

    OptionCategory(String heading, int order, ConfigSupportLevel supportLevel) {
        this.order = order;
        this.supportLevel = supportLevel;
        this.heading = getHeadingBySupportLevel(heading);
    }

    public String getHeading() {
        return this.heading;
    }

    public int getOrder() {
        return this.order;
    }

    public ConfigSupportLevel getSupportLevel() {
        return this.supportLevel;
    }

    private String getHeadingBySupportLevel(String heading) {
        return switch (supportLevel) {
            case EXPERIMENTAL -> heading + " (Experimental)";
            case PREVIEW -> heading + " (Preview)";
            case DEPRECATED -> heading + " (Deprecated)";
            default -> heading;
        };
    }

    public static OptionCategory fromHeading(String heading) {
        for (OptionCategory category : OptionCategory.values()) {
            if (category.getHeading().equals(heading)) {
                return category;
            }
        }
        throw new RuntimeException("Could not find category with heading " + heading);
    }
}
