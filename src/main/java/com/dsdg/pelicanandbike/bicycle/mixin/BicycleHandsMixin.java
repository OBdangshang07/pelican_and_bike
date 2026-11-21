package com.dsdg.pelicanandbike.bicycle.mixin;

import com.dsdg.pelicanandbike.bicycle.BicycleEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class BicycleHandsMixin {
    @Inject(method="renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",at=@At("HEAD"),cancellable=true)
    private void bicycle$hands(float delta,MatrixStack m,VertexConsumerProvider.Immediate v,ClientPlayerEntity p,int light,CallbackInfo ci) {
        if(p.getVehicle() instanceof BicycleEntity)ci.cancel();
    }
}
