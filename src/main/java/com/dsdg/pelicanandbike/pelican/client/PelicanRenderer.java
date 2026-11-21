package com.dsdg.pelicanandbike.pelican.client;

import com.dsdg.pelicanandbike.bicycle.BicycleEntity;
import com.dsdg.pelicanandbike.pelican.PelicanEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

final class PelicanRenderer extends GeoEntityRenderer<PelicanEntity> {
    PelicanRenderer(EntityRendererFactory.Context context){super(context,new PelicanModel());shadowRadius=.40f;}
    static float mountedBlend(PelicanEntity bird,float dt){
        if(bird.pose()==PelicanEntity.Pose.STEP_OFF)return 1-PelicanContact.smooth((bird.elapsed()+dt)/13f);
        return bird.hasVehicle()?PelicanContact.smooth((bird.elapsed()+dt)/13f):0;
    }
    @Override protected void applyRotations(PelicanEntity bird,MatrixStack m,float age,float yaw,float dt){
        BicycleEntity b=bird.displayBike();
        if(b==null||(!bird.hasVehicle()&&bird.pose()!=PelicanEntity.Pose.STEP_OFF)){super.applyRotations(bird,m,age,yaw,dt);return;}
        float blend=mountedBlend(bird,dt);
        Vec3d delta=b.getLerpedPos(dt).subtract(bird.getLerpedPos(dt));m.translate(delta.x,delta.y,delta.z);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-MathHelper.lerpAngleDegrees(dt,b.prevYaw,b.getYaw())));
        m.translate(-bird.side()*.92*(1-blend),Math.sin(Math.PI*blend)*.16,.04*(1-blend));
        m.multiply(RotationAxis.POSITIVE_Z.rotation(-b.sample(0,0,dt)*blend));m.multiply(RotationAxis.POSITIVE_X.rotation(b.sample(0,2,dt)*blend));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bird.side()*90*(1-blend)));
    }
}
