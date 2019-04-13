package org.floens.chan.core.model.export;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ExportedSite {
    @SerializedName("site_id")
    private int siteId;
    @SerializedName("configuration")
    private String configuration;
    @SerializedName("order")
    private int order;
    @SerializedName("user_settings")
    private String userSettings;
    @SerializedName("exported_pins")
    private List<ExportedPin> exportedPins;

    public ExportedSite(
            int siteId,
            String configuration,
            int order,
            String userSettings,
            List<ExportedPin> exportedPins
    ) {
        this.siteId = siteId;
        this.configuration = configuration;
        this.order = order;
        this.userSettings = userSettings;
        this.exportedPins = exportedPins;
    }

    public int getSiteId() {
        return siteId;
    }

    public String getConfiguration() {
        return configuration;
    }

    public int getOrder() {
        return order;
    }

    public String getUserSettings() {
        return userSettings;
    }

    public List<ExportedPin> getExportedPins() {
        return exportedPins;
    }
}
