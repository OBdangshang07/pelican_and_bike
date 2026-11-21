package com.dsdg.pelicanandbike.pelican.client;

import com.dsdg.pelicanandbike.pelican.*;
import com.dsdg.pelicanandbike.bicycle.BicycleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

final class PelicanModel extends GeoModel<PelicanEntity> {
    private static final Identifier NEAR=PelicanModule.id("geo/pelican.geo.json"),FAR=PelicanModule.id("geo/pelican_lod.geo.json"),
        TEX=PelicanModule.id("textures/entity/pelican/pelican.png"),ANIM=PelicanModule.id("animations/pelican.animation.json");
    public Identifier getModelResource(PelicanEntity b){var c=MinecraftClient.getInstance().getCameraEntity();return c!=null&&c.squaredDistanceTo(b)>576?FAR:NEAR;}
    public Identifier getTextureResource(PelicanEntity b){return TEX;}
    public Identifier getAnimationResource(PelicanEntity b){return ANIM;}
    private void offset(String n,Vector3f v){var bone=getAnimationProcessor().getBone(n);if(bone!=null){bone.setPosX(-v.x);bone.setPosY(v.y);bone.setPosZ(v.z);}}
    private void addRotation(String n,float x,float y,float z){var b=getAnimationProcessor().getBone(n);if(b!=null){b.setRotX(b.getRotX()+x);b.setRotY(b.getRotY()+y);b.setRotZ(b.getRotZ()+z);}}
    private void reachRotation(String name,Quaternionf target,float weight){
        var b=getAnimationProcessor().getBone(name);if(b==null)return;
        var xyz=new Quaternionf().rotationZYX(b.getRotZ(),b.getRotY(),b.getRotX()).slerp(target,weight).getEulerAnglesZYX(new Vector3f());
        b.setRotX(xyz.x);b.setRotY(xyz.y);b.setRotZ(xyz.z);
    }
    private void jointSegment(String n,Vector3f start,Vector3f end,Vector3f rest,float length){
        var b=getAnimationProcessor().getBone(n);if(b==null)return;
        offset(n,new Vector3f(start).sub(rest));var d=new Vector3f(end).sub(start);
        b.setScaleY(d.length()/length);
        b.setRotX((float)Math.atan2(-d.z,Math.hypot(d.y,d.x)));
        b.setRotZ((float)Math.atan2(d.x,-d.y));b.setRotY(0);
    }
    @Override public void setCustomAnimations(PelicanEntity bird,long id,AnimationState<PelicanEntity> state){
        super.setCustomAnimations(bird,id,state);
        float t=state.getPartialTick(),time=bird.age+t;BicycleEntity bike=bird.displayBike();
        float blend=bike==null?0:PelicanRenderer.mountedBlend(bird,t);
        boolean riding=blend>0;
        float phase=riding?bike.sample(1,2,t):0,steer=riding?bike.sample(0,1,t):0;
        int flags=riding?bike.flags():0;
        boolean water=bird.isTouchingWater(),flight=bird.pose()==PelicanEntity.Pose.FLAP||bird.pose()==PelicanEntity.Pose.GLIDE;
        float walking=Math.min(1,bird.limbAnimator.getSpeed(t)*3.5f);
        float elev=blend*(7.05f+((flags&16)!=0?.52f:0)+.14f*(float)Math.cos(2*phase));
        float dive=riding?bike.sample(2,2,t):0;
        float aft=riding?((flags&2048)!=0?.48f:0)-((flags&8192)!=0?.42f:0):0;
        float bob=elev+(water&&!riding?-2.0f+.18f*(float)Math.sin(time*.12):0)-dive*3;
        offset("breast",new Vector3f(0,bob,aft));
        float skid=riding?bike.sample(3,1,t):0;
        if(riding){
            float shift=(flags&1024)!=0?1:0;
            addRotation("neck_base",-.16f*shift,steer*.58f+shift*.31f,0);
            addRotation("head",-.30f*shift,steer*.65f+shift*.25f,0);
            addRotation("tail",0,-skid*.9f,bike.sample(0,0,t)*.32f);
        }
        for(int side:new int[]{-1,1}){
            String s=side<0?"l":"r";float swing=(float)Math.sin(bird.limbAnimator.getPos(t)*1.15+side*Math.PI/2);
            if(water)swing=(float)Math.sin(time*.31+side*Math.PI/2);
            float weight=water?1:walking;
            Vector3f hip=new Vector3f(side*1.64f,6.55f+bob*.81f,1.1f+aft);
            Vector3f sole=new Vector3f(side*1.76f,.19f+Math.max(0,swing)*1.35f*weight,.75f+swing*1.15f*weight);
            if(water)sole.add(0,2.1f,swing*1.55f);
            if(flight)sole.set(side*2.05f,5.8f,4.55f);
            if(riding){
                var target=PelicanContact.pedal(side,phase);
                if((flags&(side<0?128:256))!=0&&Math.abs(bike.sample(3,0,t))<.8)target.set(side*5.1f,.13f,1.7f);
                sole.lerp(target,PelicanContact.smooth((blend-(side==bird.side()?.08f:.27f))/(side==bird.side()?.92f:.73f)));
            }else if(bird.pose()==PelicanEntity.Pose.BOARD && side==bird.side())sole.y+=Math.min(2.6f,bird.elapsed()*.25f);
            Vector3f ankle=new Vector3f(sole).add(-side*.0415f*blend,.19f-.0535f*blend,.23355f*blend),knee=PelicanContact.joint(hip,ankle,side);
            jointSegment("thigh_"+s,hip,knee,new Vector3f(side*1.64f,6.55f,1.1f),3.55f);
            jointSegment("shin_"+s,knee,ankle,new Vector3f(side*1.69f,3.45f,.12f),3.05f);
            offset("foot_"+s,new Vector3f(ankle).sub(side*1.76f,.38f,.75f));
            for(int i=0;i<4;i++){
                var digit=getAnimationProcessor().getBone("digit_"+s+i);
                if(digit!=null)digit.setRotY((i-1.5f)*side*.12f*Math.max(0,swing)*weight*(1-blend));
            }
            if(riding){
                float pressure=bike.sample(2,side<0?0:1,t);
                var target=PelicanContact.grip(side,steer,pressure);
                float save=MathHelper.clamp((side*skid-.075f)*3,0,1);
                target.add(side*save*2,save*2,0);
                var reach=PelicanContact.reach(side,target,bob,aft);
                reachRotation("wing_"+s,reach.shoulder(),blend);
                reachRotation("elbow_"+s,reach.elbow(),blend);
                var folded=new Quaternionf(reach.shoulder()).mul(reach.elbow()).invert().rotateY(side*save*.67f).rotateX(-pressure*.11f);
                reachRotation("wrist_"+s,folded,blend);
            }
        }
    }
}
