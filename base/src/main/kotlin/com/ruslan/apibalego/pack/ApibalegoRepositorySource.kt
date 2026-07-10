package com.ruslan.apibalego.pack

import net.minecraft.network.chat.Component
import net.minecraft.server.packs.FilePackResources
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.FolderRepositorySource
import net.minecraft.server.packs.repository.Pack
import net.minecraft.world.level.validation.DirectoryValidator
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.function.Consumer

/**
 * Scans [packsFolder] for gamemaster-synced packs and always loads them as required (always
 * active, not removable from the pack screen)
 */
class ApibalegoRepositorySource(
    private val packsFolder: Path,
    private val packType: PackType,
) : FolderRepositorySource(packsFolder, packType, ApibalegoPackSource.INSTANCE, DirectoryValidator { true }) {
    override fun loadPacks(packAdder: Consumer<Pack>) {
        if (!Files.isDirectory(packsFolder)) {
            Files.createDirectories(packsFolder)
        }

        Files.list(packsFolder).use { stream ->
            stream.filter(::isValidPack).forEach { packPath ->
                val packName = packPath.fileName.toString()
                val locationInfo = PackLocationInfo(packName, Component.literal(packName), ApibalegoPackSource.INSTANCE, Optional.empty())
                val selectionConfig = PackSelectionConfig(true, Pack.Position.TOP, false)
                val pack = Pack.readMetaAndCreate(locationInfo, resourcesSupplier(packPath), packType, selectionConfig)
                if (pack != null) packAdder.accept(pack)
            }
        }
    }

    private fun isValidPack(path: Path): Boolean {
        val file = path.toFile()
        return (file.isFile && file.name.endsWith(".zip")) || (file.isDirectory && file.resolve("pack.mcmeta").isFile)
    }

    private fun resourcesSupplier(path: Path): Pack.ResourcesSupplier {
        val file = path.toFile()
        return if (file.isFile && file.name.endsWith(".zip")) {
            FilePackResources.FileResourcesSupplier(path)
        } else {
            PathPackResources.PathResourcesSupplier(path)
        }
    }
}
