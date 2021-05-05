/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.repository

import com.github.adamantcheese.chan.core.database.DatabaseHelper
import com.github.adamantcheese.chan.core.database.DatabaseUtils
import com.github.adamantcheese.chan.core.di.AppModule
import com.github.adamantcheese.chan.core.model.export.*
import com.github.adamantcheese.chan.core.model.orm.*
import com.github.adamantcheese.chan.core.repository.ImportExportRepository.ImportExport.Export
import com.github.adamantcheese.chan.core.repository.ImportExportRepository.ImportExport.Import
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.EMPTY_JSON
import com.github.adamantcheese.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.fsaf.file.FileDescriptorMode
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.sql.SQLException
import java.util.*
import java.util.regex.Pattern

class ImportExportRepository
constructor(
        private val databaseHelper: DatabaseHelper,
        private val fileManager: FileManager
) {

    fun exportTo(settingsFile: ExternalFile, isNewFile: Boolean, callbacks: ImportExportCallbacks) {
        DatabaseUtils.runTask {
            try {
                val appSettings = readSettingsFromDatabase()
                if (appSettings.isEmpty) {
                    callbacks.onNothingToImportExport(Export)
                    return@runTask
                }

                val json = AppModule.gson.toJson(appSettings)

                if (!fileManager.exists(settingsFile) || !fileManager.canWrite(settingsFile)) {
                    throw IOException(
                            "Something wrong with export file (Can't write or it doesn't exist) "
                                    + settingsFile.getFullPath()
                    )
                }

                // If the user has opened an old settings file we need to use WriteTruncate mode
                // so that there no leftovers of the old file after writing the settings.
                // Otherwise use Write mode
                var fdm = FileDescriptorMode.WriteTruncate
                if (isNewFile) {
                    fdm = FileDescriptorMode.Write
                }

                fileManager.withFileDescriptor(settingsFile, fdm) { fileDescriptor ->
                    FileWriter(fileDescriptor).use { writer ->
                        writer.write(json)
                        writer.flush()
                    }

                    Logger.d(TAG, "Exporting done!")
                    callbacks.onSuccess(Export)
                }

            } catch (error: Throwable) {
                Logger.e(TAG, "Error while trying to export settings", error)

                deleteExportFile(settingsFile)
                callbacks.onError(error, Export)
            }
        }
    }

    fun importFrom(settingsFile: ExternalFile, callbacks: ImportExportCallbacks) {
        DatabaseUtils.runTask {
            try {
                if (!fileManager.exists(settingsFile)) {
                    Logger.e(TAG, "There is nothing to import, importFile does not exist "
                            + settingsFile.getFullPath())
                    callbacks.onNothingToImportExport(Import)
                    return@runTask
                }

                if (!fileManager.canRead(settingsFile)) {
                    throw IOException(
                            "Something wrong with import file (Can't read or it doesn't exist) "
                                    + settingsFile.getFullPath()
                    )
                }

                fileManager.withFileDescriptor(
                        settingsFile,
                        FileDescriptorMode.Read
                ) { fileDescriptor ->
                    FileReader(fileDescriptor).use { reader ->
                        val appSettings = AppModule.gson.fromJson(reader, ExportedAppSettings::class.java)

                        if (appSettings.isEmpty) {
                            Logger.d(TAG, "There is nothing to import, appSettings is empty")
                            callbacks.onNothingToImportExport(Import)
                            return@use
                        }

                        writeSettingsToDatabase(appSettings)

                        Logger.d(TAG, "Importing done!")
                        callbacks.onSuccess(Import)
                    }
                }

            } catch (error: Throwable) {
                Logger.e(TAG, "Error while trying to import settings", error)
                callbacks.onError(error, Import)
            }
        }
    }

    private fun deleteExportFile(exportFile: AbstractFile) {
        if (!fileManager.delete(exportFile)) {
            Logger.w(TAG, "Could not delete export file " + exportFile.getFullPath())
        }
    }

    @Throws(SQLException::class, IOException::class, DowngradeNotSupportedException::class)
    private fun writeSettingsToDatabase(appSettingsParam: ExportedAppSettings) {
        var appSettings = appSettingsParam

        if (appSettings.version < CURRENT_EXPORT_SETTINGS_VERSION) {
            appSettings = onUpgrade(appSettings.version, appSettings)
        } else if (appSettings.version > CURRENT_EXPORT_SETTINGS_VERSION) {
            // we don't support settings downgrade so just notify the user about it
            throw DowngradeNotSupportedException("You are attempting to import settings with " +
                    "version higher than the current app's settings version (downgrade). " +
                    "This is not supported so nothing will be imported."
            )
        }

        // recreate tables from scratch, because we need to reset database IDs as well
        databaseHelper.connectionSource.use { cs ->
            databaseHelper.dropTables(cs)
            databaseHelper.createTables(cs)
        }

        for (exportedBoard in appSettings.exportedBoards) {
            databaseHelper.boardDao.createIfNotExists(Board(
                    exportedBoard.siteId,
                    exportedBoard.isSaved,
                    exportedBoard.order,
                    exportedBoard.name,
                    exportedBoard.code,
                    exportedBoard.isWorkSafe,
                    exportedBoard.perPage,
                    exportedBoard.pages,
                    exportedBoard.maxFileSize,
                    exportedBoard.maxWebmSize,
                    exportedBoard.maxCommentChars,
                    exportedBoard.bumpLimit,
                    exportedBoard.imageLimit,
                    exportedBoard.cooldownThreads,
                    exportedBoard.cooldownReplies,
                    exportedBoard.cooldownImages,
                    exportedBoard.isSpoilers,
                    exportedBoard.customSpoilers,
                    exportedBoard.isUserIds,
                    exportedBoard.isCodeTags,
                    exportedBoard.isPreuploadCaptcha,
                    exportedBoard.isCountryFlags,
                    exportedBoard.boardFlags,
                    exportedBoard.isMathTags,
                    exportedBoard.description ?: "",
                    exportedBoard.isArchive
            ))
        }

        for (exportedSite in appSettings.exportedSites) {
            val inserted = databaseHelper.siteModelDao.createIfNotExists(SiteModel(
                    exportedSite.siteId,
                    exportedSite.configuration,
                    exportedSite.userSettings,
                    exportedSite.order,
                    exportedSite.classId
            ))

            for (exportedPin in exportedSite.exportedPins) {
                val exportedLoadable = exportedPin.exportedLoadable ?: continue

                val loadable = Loadable.importLoadable(
                        inserted.id,
                        exportedLoadable.mode,
                        exportedLoadable.boardCode,
                        exportedLoadable.no,
                        exportedLoadable.title,
                        exportedLoadable.listViewIndex,
                        exportedLoadable.listViewTop,
                        exportedLoadable.lastViewed,
                        exportedLoadable.lastLoaded
                )

                val insertedLoadable = databaseHelper.loadableDao.createIfNotExists(loadable)
                val pin = Pin(
                        insertedLoadable,
                        exportedPin.isWatching,
                        exportedPin.watchLastCount,
                        exportedPin.watchNewCount,
                        exportedPin.quoteLastCount,
                        exportedPin.quoteNewCount,
                        exportedPin.isError,
                        exportedPin.order,
                        exportedPin.isArchived
                )
                databaseHelper.pinDao.createIfNotExists(pin)
            }
        }

        for (exportedFilter in appSettings.exportedFilters) {
            databaseHelper.filterDao.createIfNotExists(Filter(
                    exportedFilter.isEnabled,
                    exportedFilter.type,
                    exportedFilter.pattern,
                    exportedFilter.isAllBoards,
                    exportedFilter.boards,
                    exportedFilter.action,
                    exportedFilter.color,
                    exportedFilter.applyToReplies,
                    exportedFilter.order,
                    exportedFilter.onlyOnOP,
                    exportedFilter.applyToSaved
            ))
        }

        for (exportedPostHide in appSettings.exportedPostHides) {
            databaseHelper.postHideDao.createIfNotExists(PostHide(
                    exportedPostHide.site,
                    exportedPostHide.board,
                    exportedPostHide.no))
        }

        ChanSettings.deserializeFromString(appSettingsParam.settings)
    }

    private fun onUpgrade(version: Int, appSettings: ExportedAppSettings): ExportedAppSettings {
        if (version < 2) {
            //clear the post hides for version 1, threadNo field was added
            appSettings.exportedPostHides = ArrayList()
        }

        if (version < 3) {
            //clear the site model usersettings to be an empty JSON map for version 2,
            // as they won't parse correctly otherwise
            for (site in appSettings.exportedSites) {
                site.userSettings = EMPTY_JSON
            }
        }

        //can't directly use gson here, gotta use regex instead
        //I don't know why, but for some reason Android fails to compile this without the redundant escape??
        @Suppress("RegExpRedundantEscape") val oldConfigPattern = Pattern.compile("\\{\"internal_site_id\":(\\d+),\"external\":.+\\}")

        if (version < 4) {
            //55chan and 8chan were removed for this version
            var chan8: ExportedSite? = null
            var chan55: ExportedSite? = null

            for (site in appSettings.exportedSites) {
                val matcher = oldConfigPattern.matcher(site.configuration.toString())
                if (matcher.matches()) {
                    val classID = matcher.group(1)?.let { Integer.parseInt(it) }
                    if (classID == 1 && chan8 == null) {
                        chan8 = site
                    }

                    if (classID == 7 && chan55 == null) {
                        chan55 = site
                    }
                }
            }

            if (chan55 != null) {
                deleteExportedSite(chan55, appSettings)
            }

            if (chan8 != null) {
                deleteExportedSite(chan8, appSettings)
            }
        }

        if (version < 5) {
            // siteconfig class removed, move stuff over
            for (site in appSettings.exportedSites) {
                val matcher = oldConfigPattern.matcher(site.configuration.toString())
                if (matcher.matches()) {
                    val classID = matcher.group(1)?.let { Integer.parseInt(it) }
                    if (classID != null) {
                        site.classId = classID
                    }
                }
            }
        }

        if (version < 6) {
            for (board in appSettings.exportedBoards) {
                board.boardFlags = HashMap()
            }
        }
        return appSettings
    }

    @Throws(java.sql.SQLException::class, IOException::class)
    private fun readSettingsFromDatabase(): ExportedAppSettings {
        val sitesMap = fillSitesMap()
        val loadableMap = fillLoadablesMap()

        val pins = HashSet(databaseHelper.pinDao.queryForAll())
        val toExportMap = HashMap<SiteModel, MutableList<ExportedPin>>()

        for (siteModel in sitesMap.values) {
            toExportMap[siteModel] = ArrayList()
        }

        for (pin in pins) {
            val loadable = loadableMap[pin.loadable.id]
                    ?: throw NullPointerException("Could not find Loadable by pin.loadable.id "
                            + pin.loadable.id)

            val siteModel = sitesMap[loadable.siteId]
                    ?: throw NullPointerException("Could not find siteModel by loadable.siteId "
                            + loadable.siteId)

            val exportedLoadable = ExportedLoadable(
                    loadable.boardCode,
                    loadable.id.toLong(),
                    loadable.lastLoaded,
                    loadable.lastViewed,
                    loadable.listViewIndex,
                    loadable.listViewTop,
                    loadable.mode,
                    loadable.no,
                    loadable.siteId,
                    loadable.title,
                    loadable.thumbnailUrl?.toString()
            )

            val exportedPin = ExportedPin(
                    pin.archived,
                    pin.id,
                    pin.isError,
                    loadable.id,
                    pin.order,
                    pin.quoteLastCount,
                    pin.quoteNewCount,
                    pin.watchLastCount,
                    pin.watchNewCount,
                    pin.watching,
                    exportedLoadable
            )

            toExportMap[siteModel]!!.add(exportedPin)
        }

        val exportedSites = ArrayList<ExportedSite>()

        for ((key, value) in toExportMap) {
            val exportedSite = ExportedSite(
                    key.id,
                    key.configuration,
                    key.order,
                    key.userSettings,
                    key.classID,
                    value
            )

            exportedSites.add(exportedSite)
        }

        val exportedBoards = databaseHelper.boardDao.queryForAll().map {
            ExportedBoard(
                    it.siteId,
                    it.saved,
                    it.order,
                    it.name,
                    it.code,
                    it.workSafe,
                    it.perPage,
                    it.pages,
                    it.maxFileSize,
                    it.maxWebmSize,
                    it.maxCommentChars,
                    it.bumpLimit,
                    it.imageLimit,
                    it.cooldownThreads,
                    it.cooldownReplies,
                    it.cooldownImages,
                    it.spoilers,
                    it.customSpoilers,
                    it.userIds,
                    it.codeTags,
                    it.preuploadCaptcha,
                    it.countryFlags,
                    it.boardFlags,
                    it.mathTags,
                    it.description,
                    it.archive
            )
        }

        val exportedFilters = databaseHelper.filterDao.queryForAll().map {
            ExportedFilter(
                    it.enabled,
                    it.type,
                    it.pattern,
                    it.allBoards,
                    it.boards,
                    it.action,
                    it.color,
                    it.applyToReplies,
                    it.order,
                    it.onlyOnOP,
                    it.applyToSaved
            )
        }

        val exportedPostHides = databaseHelper.postHideDao.queryForAll().map {
            ExportedPostHide(
                    it.site,
                    it.board,
                    it.no,
                    it.wholeThread,
                    it.hide,
                    it.hideRepliesToThisPost,
                    it.threadNo
            )
        }

        val settings = ChanSettings.serializeToString()

        return ExportedAppSettings(CURRENT_EXPORT_SETTINGS_VERSION,
                exportedSites,
                exportedBoards,
                exportedFilters,
                exportedPostHides,
                settings
        )
    }

    private fun fillLoadablesMap(): Map<Int, Loadable> {
        val map = hashMapOf<Int, Loadable>()
        val loadables = databaseHelper.loadableDao.queryForAll()

        for (loadable in loadables) {
            map[loadable.id] = loadable
        }

        return map
    }

    private fun fillSitesMap(): Map<Int, SiteModel> {
        val map = hashMapOf<Int, SiteModel>()
        val sites = databaseHelper.siteModelDao.queryForAll()

        for (site in sites) {
            map[site.id] = site
        }

        return map
    }

    private fun deleteExportedSite(site: ExportedSite, appSettings: ExportedAppSettings) {
        //filters
        val filtersToDelete = ArrayList<ExportedFilter>()
        for (filter in appSettings.exportedFilters) {
            if (filter.isAllBoards || filter.boards.isNullOrEmpty()) {
                continue
            }

            val boards = checkNotNull(filter.boards)
            val splitBoards = boards.split(",".toRegex()).dropLastWhile { it.isEmpty() }

            for (uniqueId in splitBoards) {
                val split = uniqueId.split(":".toRegex()).dropLastWhile { it.isEmpty() }

                if (split.size == 2 && Integer.parseInt(split[0]) == site.siteId) {
                    filtersToDelete.add(filter)
                    break
                }
            }
        }

        appSettings.exportedFilters.removeAll(filtersToDelete)

        //boards
        val boardsToDelete = appSettings.exportedBoards.filter { it.siteId == site.siteId }
        appSettings.exportedBoards.removeAll(boardsToDelete)

        //loadables for saved threads
        val loadables = ArrayList<ExportedLoadable>()
        for (pin in site.exportedPins) {
            val loadable = pin.exportedLoadable
                    ?: continue

            if (loadable.siteId == site.siteId) {
                loadables.add(loadable)
            }
        }

        //post hides
        val hidesToDelete = appSettings.exportedPostHides.filter { it.site == site.siteId }

        appSettings.exportedPostHides.removeAll(hidesToDelete)

        //site (also removes pins and loadables)
        appSettings.exportedSites.remove(site)
    }

    enum class ImportExport {
        Import,
        Export
    }

    interface ImportExportCallbacks {
        fun onSuccess(importExport: ImportExport)
        fun onNothingToImportExport(importExport: ImportExport)
        fun onError(error: Throwable, importExport: ImportExport)
    }

    class DowngradeNotSupportedException(message: String) : Exception(message)

    companion object {
        private const val TAG = "ImportExportRepository"

        // Don't forget to change this when changing any of the Export models.
        // Also, don't forget to handle the change in the onUpgrade or onDowngrade methods
        const val CURRENT_EXPORT_SETTINGS_VERSION = 6
    }
}
