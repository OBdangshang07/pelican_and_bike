package com.dsdg.pelicanandbike.bicycle.mixin;

import com.dsdg.pelicanandbike.bicycle.BicycleEntity;
import com.dsdg.pelicanandbike.bicycle.client.BicycleClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class BicyclePlayerRenderMixin {
    @Inject(method="render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"),cancellable=true)
    private void bicycle$rig(AbstractClientPlayerEntity p,float yaw,float tick,MatrixStack m,VertexConsumerProvider v,int light,CallbackInfo ci) {
        if(p.getVehicle() instanceof BicycleEntity && BicycleClient.renderingWorld())ci.cancel();
    }
    @Inject(method="setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",at=@At("TAIL"))
    private void bicycle$fall(AbstractClientPlayerEntity p,MatrixStack m,float age,float yaw,float tick,CallbackInfo ci) {
        var fall=BicycleClient.fall(p.getUuid()); if(fall==null||p.hasVehicle()||!p.isAlive())return;
        float elapsed=p.getWorld().getTime()-fall.start()+tick;
        float recovery=fall.landed()<0?1:MathHelper.clamp(1-(p.getWorld().getTime()-fall.landed()+tick-4)/16f,0,1);
        float phase=MathHelper.clamp(elapsed/13f,0,1);
        float angle=com.dsdg.pelicanandbike.bicycle.client.RiderClips.fallDegrees(fall.side(),phase)*recovery;
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.wrapDegrees(yaw-fall.yaw())*recovery));
        if(fall.landed()>=0){
            double radians=Math.toRadians(angle),cos=Math.cos(radians);
            double lowest=.8+Math.min(-.8*cos,cos)-.21*Math.abs(Math.sin(radians));
            float contact=MathHelper.clamp((p.getWorld().getTime()-fall.landed()+tick)/3f,0,1);
            m.translate(0,-lowest*contact,0);
        }
        m.translate(0,.8,0);
        m.multiply((fall.side()==2?RotationAxis.POSITIVE_X:RotationAxis.POSITIVE_Z).rotationDegrees(-angle));
        m.translate(0,-.8,0);
    }
}
