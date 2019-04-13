package org.floens.chan.core.model.export;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.core.repository.ImportExportRepository.CURRENT_EXPORT_SETTINGS_VERSION;

public class ExportedAppSettings {
    @SerializedName("version")
    private int version = CURRENT_EXPORT_SETTINGS_VERSION;
    @SerializedName("exported_sites")
    private List<ExportedSite> exportedSites;
    @SerializedName("exported_boards")
    private List<ExportedBoard> exportedBoards;
    @SerializedName("exported_filters")
    private List<ExportedFilter> exportedFilters;
    @SerializedName("exported_post_hides")
    private List<ExportedPostHide> exportedPostHides;
    @SerializedName("exported_settings")
    private String settings;

    public ExportedAppSettings(
            List<ExportedSite> exportedSites,
            List<ExportedBoard> exportedBoards,
            List<ExportedFilter> exportedFilters,
            List<ExportedPostHide> exportedPostHides,
            String settings
    ) {
        this.exportedSites = exportedSites;
        this.exportedBoards = exportedBoards;
        this.exportedFilters = exportedFilters;
        this.exportedPostHides = exportedPostHides;
        this.settings = settings;
    }

    public static ExportedAppSettings empty() {
        return new ExportedAppSettings(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                ""
        );
    }

    /**
     * Sites and boards are important we can't export almost nothing else without them
     * (probably only settings)
     */
    public boolean isEmpty() {
        return exportedSites.isEmpty() && exportedBoards.isEmpty();
    }

    public List<ExportedSite> getExportedSites() {
        return exportedSites;
    }

    public List<ExportedBoard> getExportedBoards() {
        return exportedBoards;
    }

    public List<ExportedFilter> getExportedFilters() {
        return exportedFilters;
    }

    public List<ExportedPostHide> getExportedPostHides() {
        return exportedPostHides;
    }

    public int getVersion() {
        return version;
    }

    public String getSettings() {
        return settings;
    }
}
