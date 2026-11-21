package com.dsdg.pelicanandbike.bicycle.mixin;

import com.dsdg.pelicanandbike.bicycle.BicycleEntity;
import com.dsdg.pelicanandbike.bicycle.client.BicycleClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class BicycleCameraMixin {
    @Inject(method="renderWorld",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/Camera;getPitch()F",ordinal=0))
    private void bicycle$lean(float delta,long limit,MatrixStack m,CallbackInfo ci) {
        var c=MinecraftClient.getInstance();
        if(c.options.getPerspective().isFirstPerson() && c.getCameraEntity()!=null && c.getCameraEntity().getVehicle() instanceof BicycleEntity b)
            m.multiply(RotationAxis.POSITIVE_Z.rotation(b.sample(0,0,delta)*BicycleClient.cameraScale));
    }
}
