package com.ruslan.apibalego.mixin.client;

import com.ruslan.apibalego.client.handlers.MainMenuMessageHandler;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SplashManager.class)
public class SplashManagerMixin {
    @Inject(method = "getSplash", at = @At("HEAD"), cancellable = true)
    private void apibalego$replaceSplash(CallbackInfoReturnable<SplashRenderer> cir) {
        SplashRenderer replacement = MainMenuMessageHandler.getCustomSplash();
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
