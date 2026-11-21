package com.dsdg.pelicanandbike.bicycle.mixin;

import com.dsdg.pelicanandbike.bicycle.client.BicycleClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class BicycleRecoveryInputMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void bicycle$recover(boolean slow,float slowFactor,CallbackInfo ci) {
        var player=MinecraftClient.getInstance().player;
        if(player==null||player.hasVehicle()||player.isSpectator()||!player.isAlive())return;
        var fall=BicycleClient.fall(player.getUuid());if(fall==null)return;
        float control=fall.landed()<0?.15f:MathHelper.clamp((player.getWorld().getTime()-fall.landed()-3)/10f,0,1);
        var input=(KeyboardInput)(Object)this;
        input.movementForward*=control;input.movementSideways*=control;
        if(control<.6f)input.jumping=false;
    }
}
