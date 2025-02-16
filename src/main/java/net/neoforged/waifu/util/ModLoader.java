package net.neoforged.waifu.util;

public enum ModLoader {
    NEOFORGE("https://github.com/neoforged.png");

    private final String logo;

    ModLoader(String logo) {
        this.logo = logo;
    }

    public String getLogoUrl() {
        return logo;
    }
}
