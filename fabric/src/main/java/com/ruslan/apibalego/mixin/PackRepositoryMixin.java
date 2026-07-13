package com.ruslan.apibalego.mixin;

import com.ruslan.apibalego.ApibalegoMod;
import com.ruslan.apibalego.pack.PreloadPackSync;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Similar result as Neoforge's AddPackFindersEvent
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Mutable
    @Shadow
    @Final
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void apibalego$addServerDatapackSource(RepositorySource[] sources, CallbackInfo ci) {
        boolean isServerData = false;
        for (RepositorySource source : sources) {
            if (source instanceof ServerPacksSource) {
                isServerData = true;
                break;
            }
        }
        if (!isServerData) {
            return;
        }

        ApibalegoMod.preInit(FabricLoader.getInstance().getGameDir());

        PreloadPackSync.INSTANCE.preloadServerDatapacks();
        Set<RepositorySource> mutable = new LinkedHashSet<>(this.sources);
        mutable.add(PreloadPackSync.INSTANCE.serverRepositorySource());
        this.sources = mutable;
    }
}
