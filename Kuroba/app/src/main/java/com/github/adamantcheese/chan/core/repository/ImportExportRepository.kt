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

import android.annotation.SuppressLint
import android.text.TextUtils
import com.github.adamantcheese.chan.core.database.DatabaseHelper
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.model.export.*
import com.github.adamantcheese.chan.core.model.json.site.SiteConfig
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
import com.google.gson.Gson
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.sql.SQLException
import java.util.*
import javax.inject.Inject

class ImportExportRepository @Inject
constructor(
        private val databaseManager: DatabaseManager,
        private val databaseHelper: DatabaseHelper,
        private val gson: Gson,
        private val fileManager: FileManager
) {

    fun exportTo(settingsFile: ExternalFile, isNewFile: Boolean, callbacks: ImportExportCallbacks) {
        databaseManager.runTask {
            try {
                val appSettings = readSettingsFromDatabase()
                if (appSettings.isEmpty) {
                    callbacks.onNothingToImportExport(Export)
                    return@runTask
                }

                val json = gson.toJson(appSettings)

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
        databaseManager.runTask {
            try {
                if (!fileManager.exists(settingsFile)) {
                    Logger.i(TAG, "There is nothing to import, importFile does not exist "
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
                        val appSettings = gson.fromJson(reader, ExportedAppSettings::class.java)

                        if (appSettings.isEmpty) {
                            Logger.i(TAG, "There is nothing to import, appSettings is empty")
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
            assert(exportedBoard.description != null)
            databaseHelper.boardsDao.createIfNotExists(Board(
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
                    exportedBoard.isMathTags,
                    exportedBoard.description ?: "",
                    exportedBoard.isArchive
            ))
        }

        for (exportedSite in appSettings.exportedSites) {
            val inserted = databaseHelper.siteDao.createIfNotExists(SiteModel(
                    exportedSite.siteId,
                    exportedSite.configuration,
                    exportedSite.userSettings,
                    exportedSite.order
            ))

            val exportedSavedThreads = appSettings.exportedSavedThreads

            for (exportedPin in exportedSite.exportedPins) {
                val exportedLoadable = exportedPin.exportedLoadable
                if (exportedLoadable == null) {
                    continue
                }

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
                val exportedSavedThread = findSavedThreadByOldLoadableId(
                        exportedSavedThreads,
                        exportedLoadable.loadableId.toInt())

                // ExportedSavedThreads may have their loadable ids noncontiguous. Like 1,3,4,5,21,152.
                // SQLite does not like it and will be returning to us contiguous ids ignoring our ids.
                // This will create a situation where savedThread.loadableId may not have a loadable.
                // So we need to fix this by finding a saved thread by old loadable id and updating
                // it's loadable id with the newly inserted id.
                if (exportedSavedThread != null) {
                    exportedSavedThread.loadableId = insertedLoadable.id

                    databaseHelper.savedThreadDao.createIfNotExists(SavedThread(
                            exportedSavedThread.isFullyDownloaded,
                            exportedSavedThread.isStopped,
                            exportedSavedThread.lastSavedPostNo,
                            exportedSavedThread.loadableId
                    ))
                }

                val pin = Pin(
                        insertedLoadable,
                        exportedPin.isWatching,
                        exportedPin.watchLastCount,
                        exportedPin.watchNewCount,
                        exportedPin.quoteLastCount,
                        exportedPin.quoteNewCount,
                        exportedPin.isError,
                        exportedPin.thumbnailUrl,
                        exportedPin.order,
                        exportedPin.isArchived,
                        exportedPin.pinType
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

    private fun findSavedThreadByOldLoadableId(
            exportedSavedThreads: List<ExportedSavedThread>,
            oldLoadableId: Int): ExportedSavedThread? {
        for (exportedSavedThread in exportedSavedThreads) {
            if (exportedSavedThread.loadableId == oldLoadableId) {
                return exportedSavedThread
            }
        }

        return null
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

        if (version < 4) {
            //55chan and 8chan were removed for this version
            var chan8: ExportedSite? = null
            var chan55: ExportedSite? = null

            for (site in appSettings.exportedSites) {
                val config = gson.fromJson(site.configuration, SiteConfig::class.java)

                if (config.classId == 1 && chan8 == null) {
                    chan8 = site
                }

                if (config.classId == 7 && chan55 == null) {
                    chan55 = site
                }
            }

            if (chan55 != null) {
                deleteExportedSite(chan55, appSettings)
            }

            if (chan8 != null) {
                deleteExportedSite(chan8, appSettings)
            }
        }

        return appSettings
    }

    @Throws(java.sql.SQLException::class, IOException::class)
    private fun readSettingsFromDatabase(): ExportedAppSettings {
        @SuppressLint("UseSparseArrays")
        val sitesMap = fillSitesMap()

        @SuppressLint("UseSparseArrays")
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
                    loadable.title
            )

            // When exporting a localThreadLocation that points to a directory located at places like
            // sd-card we want to export pins without "download thread" flag because when
            // importing settings back after app uninstall or when importing the on another
            // phone all of the SAF base directories will become unavailable due to how SAF works.
            // So the user will have to choose the base directories again and then resume threads
            // downloading manually. We also need to set the "isStopped" flag to true for
            // ExportedSavedThread.
            val pinType = if (ChanSettings.localThreadLocation.isSafDirActive()) {
                PinType.removeDownloadNewPostsFlag(pin.pinType)
            } else {
                pin.pinType
            }

            val exportedPin = ExportedPin(
                    pin.archived,
                    pin.id,
                    pin.isError,
                    loadable.id,
                    pin.order,
                    pin.quoteLastCount,
                    pin.quoteNewCount,
                    pin.thumbnailUrl,
                    pin.watchLastCount,
                    pin.watchNewCount,
                    pin.watching,
                    exportedLoadable,
                    pinType
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
                    value
            )

            exportedSites.add(exportedSite)
        }

        val exportedBoards = ArrayList<ExportedBoard>()

        for (board in databaseHelper.boardsDao.queryForAll()) {
            exportedBoards.add(ExportedBoard(
                    board.siteId,
                    board.saved,
                    board.order,
                    board.name,
                    board.code,
                    board.workSafe,
                    board.perPage,
                    board.pages,
                    board.maxFileSize,
                    board.maxWebmSize,
                    board.maxCommentChars,
                    board.bumpLimit,
                    board.imageLimit,
                    board.cooldownThreads,
                    board.cooldownReplies,
                    board.cooldownImages,
                    board.spoilers,
                    board.customSpoilers,
                    board.userIds,
                    board.codeTags,
                    board.preuploadCaptcha,
                    board.countryFlags,
                    board.mathTags,
                    board.description,
                    board.archive
            ))
        }

        val exportedFilters = ArrayList<ExportedFilter>()

        for (filter in databaseHelper.filterDao.queryForAll()) {
            exportedFilters.add(ExportedFilter(
                    filter.enabled,
                    filter.type,
                    filter.pattern,
                    filter.allBoards,
                    filter.boards,
                    filter.action,
                    filter.color,
                    filter.applyToReplies,
                    filter.order,
                    filter.onlyOnOP,
                    filter.applyToSaved
            ))
        }

        val exportedPostHides = ArrayList<ExportedPostHide>()

        for (threadHide in databaseHelper.postHideDao.queryForAll()) {
            exportedPostHides.add(ExportedPostHide(
                    threadHide.site,
                    threadHide.board,
                    threadHide.no,
                    threadHide.wholeThread,
                    threadHide.hide,
                    threadHide.hideRepliesToThisPost,
                    threadHide.threadNo
            ))
        }

        val exportedSavedThreads = ArrayList<ExportedSavedThread>()

        for (savedThread in databaseHelper.savedThreadDao.queryForAll()) {
            // Set the isStopped flag to true for ExportedSavedThread when localThreadLocation
            // points to a directory that uses SAF
            val isDownloadingStopped = if (ChanSettings.localThreadLocation.isSafDirActive()) {
                true
            } else {
                savedThread.isStopped
            }

            exportedSavedThreads.add(ExportedSavedThread(
                    savedThread.loadableId,
                    savedThread.lastSavedPostNo,
                    savedThread.isFullyDownloaded,
                    isDownloadingStopped
            ))
        }

        val settings = ChanSettings.serializeToString()

        return ExportedAppSettings(
                exportedSites,
                exportedBoards,
                exportedFilters,
                exportedPostHides,
                exportedSavedThreads,
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
        val sites = databaseHelper.siteDao.queryForAll()

        for (site in sites) {
            map[site.id] = site
        }

        return map
    }

    private fun deleteExportedSite(site: ExportedSite, appSettings: ExportedAppSettings) {
        //filters
        val filtersToDelete = ArrayList<ExportedFilter>()
        for (filter in appSettings.exportedFilters) {
            if (filter.isAllBoards || TextUtils.isEmpty(filter.boards)) {
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
        val boardsToDelete = ArrayList<ExportedBoard>()
        for (board in appSettings.exportedBoards) {
            if (board.siteId == site.siteId) {
                boardsToDelete.add(board)
            }
        }
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

        if (loadables.isNotEmpty()) {
            val savedThreadToDelete = ArrayList<ExportedSavedThread>()
            for (loadable in loadables) {
                //saved threads
                for (savedThread in appSettings.exportedSavedThreads) {
                    if (loadable.loadableId == savedThread.getLoadableId().toLong()) {
                        savedThreadToDelete.add(savedThread)
                    }
                }
            }
            appSettings.exportedSavedThreads.removeAll(savedThreadToDelete)
        }

        //post hides
        val hidesToDelete = ArrayList<ExportedPostHide>()
        for (hide in appSettings.exportedPostHides) {
            if (hide.site == site.siteId) {
                hidesToDelete.add(hide)
            }
        }

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
        const val CURRENT_EXPORT_SETTINGS_VERSION = 5
    }
}
