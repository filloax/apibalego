package com.ruslan.apibalego.mixin.client;

import com.ruslan.apibalego.ApibalegoMod;
import com.ruslan.apibalego.client.pack.PreloadPackSyncClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(PackRepository.class)
public abstract class ClientPackRepositoryMixin {
    @Mutable
    @Shadow
    @Final
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void apibalego$addClientResourcePackSource(RepositorySource[] sources, CallbackInfo ci) {
        boolean isClientResources = false;
        for (RepositorySource source : sources) {
            if (source instanceof ClientPackSource) {
                isClientResources = true;
                break;
            }
        }
        if (!isClientResources) {
            return;
        }

        ApibalegoMod.preInitClient(FabricLoader.getInstance().getGameDir());

        PreloadPackSyncClient.INSTANCE.preloadClientResourcePacks();
        Set<RepositorySource> mutable = new LinkedHashSet<>(this.sources);
        mutable.add(PreloadPackSyncClient.INSTANCE.clientRepositorySource());
        this.sources = mutable;
    }
}
