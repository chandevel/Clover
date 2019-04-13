package org.floens.chan.core.model.export;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import static org.floens.chan.core.repository.ImportExportRepository.CURRENT_EXPORT_SETTINGS_VERSION;

public class ExportedAppSettings {
    @SerializedName("version")
    private int version = CURRENT_EXPORT_SETTINGS_VERSION;
    @SerializedName("exported_sites")
    private List<ExportedSite> exportedSites;
    @SerializedName("exported_boards")
    private List<ExportedBoard> exportedBoards;
    @SerializedName("settings")
    private String settings;

    public ExportedAppSettings(
            List<ExportedSite> exportedSites,
            List<ExportedBoard> exportedBoards,
            String settings
    ) {
        this.exportedSites = exportedSites;
        this.exportedBoards = exportedBoards;
        this.settings = settings;
    }

    public boolean isEmpty() {
        return exportedSites.isEmpty() && exportedBoards.isEmpty();
    }

    public List<ExportedSite> getExportedSites() {
        return exportedSites;
    }

    public void setExportedSites(List<ExportedSite> exportedSites) {
        this.exportedSites = exportedSites;
    }

    public List<ExportedBoard> getExportedBoards() {
        return exportedBoards;
    }

    public void setExportedBoards(List<ExportedBoard> exportedBoards) {
        this.exportedBoards = exportedBoards;
    }

    public int getVersion() {
        return version;
    }

    public String getSettings() {
        return settings;
    }
}
