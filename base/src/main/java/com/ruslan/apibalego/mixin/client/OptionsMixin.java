package com.ruslan.apibalego.mixin.client;

import com.ruslan.apibalego.client.pack.PreloadPackSyncClient;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class OptionsMixin {
    @Inject(method = "loadSelectedResourcePacks", at = @At("TAIL"))
    private void apibalego$enablePreloadedPacks(PackRepository repository, CallbackInfo ci) {
        PreloadPackSyncClient.INSTANCE.enablePreloadedPacks((Options) (Object) this, repository);
    }
}
