package com.dsdg.pelicanandbike.bicycle.client;

import com.dsdg.pelicanandbike.bicycle.BicycleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class BicycleRenderer extends GeoEntityRenderer<BicycleEntity> {
    private final RiderMesh rider=new RiderMesh();
    public BicycleRenderer(EntityRendererFactory.Context context){ super(context,new BicycleModel());shadowRadius=.63f; }
    @Override protected void applyRotations(BicycleEntity b,MatrixStack m,float age,float yaw,float t){
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-MathHelper.lerpAngleDegrees(t,b.prevYaw,b.getYaw())));
    }
    @Override public void render(BicycleEntity b,float yaw,float t,MatrixStack m,VertexConsumerProvider v,int light){
        super.render(b,yaw,t,m,v,light);
        if(!(b.getFirstPassenger() instanceof AbstractClientPlayerEntity p) || p.isInvisible())return;
        var c=MinecraftClient.getInstance();boolean fp=c.getCameraEntity()==p && c.options.getPerspective().isFirstPerson();
        m.push();applyRotations(b,m,b.age+t,yaw,t);
        m.multiply(RotationAxis.POSITIVE_Z.rotation(-b.sample(0,0,t)));m.multiply(RotationAxis.POSITIVE_X.rotation(b.sample(0,2,t)));
        rider.draw(b,p,m,v,light,t,fp);m.pop();
    }
}
