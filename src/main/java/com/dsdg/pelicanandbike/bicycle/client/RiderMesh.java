package com.dsdg.pelicanandbike.bicycle.client;

import com.dsdg.pelicanandbike.bicycle.BicycleEntity;
import java.util.*;
import net.minecraft.client.model.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Splits the vanilla skin strips at elbow and knee. All dimensions used by IK are in blocks. */
final class RiderMesh {
    private record Box(int u,int v,int w,int h,int d,int pixelsHigh) {}
    private final Map<Box,ModelPart> pieces=new HashMap<>();
    private ModelPart mesh(Box b){
        return pieces.computeIfAbsent(b,k->{
            var data=new ModelData();
            data.getRoot().addChild("box",ModelPartBuilder.create().uv(k.u,k.v).cuboid(-k.w/2f,0,-k.d/2f,k.w,k.h,k.d),ModelTransform.NONE);
            return TexturedModelData.of(data,64,k.pixelsHigh).createModel().getChild("box");
        });
    }
    private void segment(MatrixStack m,VertexConsumerProvider v,int light,Identifier tex,Box box,Vector3f a,Vector3f b,float wx,float wz){
        Vector3f axis=new Vector3f(b).sub(a);float len=axis.length();if(len<.0001f)return;
        m.push();m.translate(a.x,a.y,a.z);
        m.multiply(new Quaternionf().rotationTo(0,-1,0,axis.x/len,axis.y/len,axis.z/len));
        m.scale(wx*16/box.w,-len*16/box.h,wz*16/box.d);
        mesh(box).render(m,v.getBuffer(RenderLayer.getEntityCutoutNoCull(tex)),light,OverlayTexture.DEFAULT_UV);
        m.pop();
    }
    private record Clothing(Identifier tex,int u,int v) {}
    private static Clothing armor(AbstractClientPlayerEntity p,EquipmentSlot slot,int u,int v) {
        if(!(p.getEquippedStack(slot).getItem() instanceof ArmorItem armor))return null;
        return new Clothing(armorTexture(armor.getMaterial().getName(),slot),u,v);
    }
    static Identifier armorTexture(String materialName,EquipmentSlot slot) {
        var name=new Identifier(materialName);
        return new Identifier(name.getNamespace(),"textures/models/armor/"+name.getPath()+"_layer_"+(slot==EquipmentSlot.LEGS?2:1)+".png");
    }
    static Vector3f joint(Vector3f root,Vector3f end,float upper,float lower,Vector3f pole){
        Vector3f axis=new Vector3f(end).sub(root);float dist=axis.length();if(dist<.001f)return new Vector3f(root);
        axis.div(dist);float reach=MathHelper.clamp(dist,Math.abs(upper-lower)+.0001f,upper+lower-.0001f);
        float along=(upper*upper-lower*lower+reach*reach)/(2*reach);
        pole.fma(-pole.dot(axis),axis);if(pole.lengthSquared()<.0001f)pole.set(1,0,0);
        return new Vector3f(root).fma(along,axis).fma((float)Math.sqrt(Math.max(0,upper*upper-along*along)),pole.normalize());
    }
    static Vector3f gripTarget(int side,float steer){
        float dx=side*4.755f/16f,dz=(-6.35f+5.8f)/16f;
        return new Vector3f(dx*(float)Math.cos(steer)-dz*(float)Math.sin(steer),15.605f/16,
            -5.8f/16+dx*(float)Math.sin(steer)+dz*(float)Math.cos(steer));
    }
    static Vector3f ankleTarget(int side,float crank){
        float a=crank+(side>0?0:(float)Math.PI);
        return new Vector3f(side*2.06f/16,.31f-.17f*(float)Math.cos(a)+.050f,.06125f+.17f*(float)Math.sin(a));
    }
    void draw(BicycleEntity bike,AbstractClientPlayerEntity p,MatrixStack m,VertexConsumerProvider v,int light,float t,boolean fp) {
        float crank=bike.sample(1,2,t),steer=bike.sample(0,1,t),lean=bike.sample(0,0,t),speed=bike.sample(3,0,t);
        int flags=bike.flags();boolean standing=(flags&16)!=0 && speed>.4, dab=(flags&384)!=0;
        String action=standing?"stand":bike.sample(3,2,t)>.3?"pedal":"coast";
        float phase=(float)(crank/(Math.PI*2));phase-=(float)Math.floor(phase);
        var base=RiderClips.sample(action,"pelvis",phase).position();
        float torso=RiderClips.sample(action,"torso",phase).rotation().x();
        float brake=Math.max(bike.sample(2,0,t),bike.sample(2,1,t));
        String overlay=null;float strength=0,time=0;
        if((flags&512)!=0){overlay="mount";time=bike.mountTime(t);strength=1;}
        else if(dab){overlay="dab";strength=1;time=.5f;}
        else if((flags&4096)!=0){overlay="landing";strength=Math.min(1,bike.sample(2,2,t)*12);time=.15f;}
        else if((flags&2048)!=0){overlay="unweight";strength=1;time=.53f;}
        else if(brake>.05){overlay="brake";strength=brake;time=brake*.6f;}
        else if(Math.abs(bike.sample(3,1,t))>.03){float skid=bike.sample(3,1,t);overlay=skid>0?"save_right":"save_left";time=.4f;strength=Math.min(1,Math.abs(skid)*3);}
        if(overlay!=null){
            var o=RiderClips.sample(overlay,"pelvis",time).position();base=base.lerp(o,strength);
            torso=MathHelper.lerp(strength,torso,RiderClips.sample(overlay,"torso",time).rotation().x());
        }
        var hips=new Vector3f(base.x()/16,.959f+base.y()/16,.222f+base.z()/16);
        float descent=MathHelper.clamp(-bike.sample(0,2,t)*2,0,1);
        hips.z+=descent*.055f+((flags&2048)!=0?.042f:0)-((flags&8192)!=0?.085f:0);
        hips.y-=descent*.044f;
        torso+=descent*12+((flags&8192)!=0?9:0);
        if(dab && (flags&128)!=0)hips.x=-Math.abs(hips.x);
        if(standing)hips.y-=bike.sample(4,1,t)*.026f;
        var neck=new Vector3f(hips).add(-lean*.075f,.56f*(float)Math.cos(Math.toRadians(torso)),-.56f*(float)Math.sin(Math.toRadians(torso)));
        Identifier skin=p.getSkinTexture();
        if(!fp){
            segment(m,v,light,skin,new Box(16,16,8,12,4,64),neck,hips,.48f,.24f);
            if(p.isPartVisible(PlayerModelPart.JACKET))segment(m,v,light,skin,new Box(16,32,8,12,4,64),neck,hips,.49f,.25f);
            var chest=armor(p,EquipmentSlot.CHEST,16,16);
            if(chest!=null)segment(m,v,light,chest.tex,new Box(16,16,8,12,4,32),neck,hips,.54f,.31f);
            m.push();m.translate(neck.x,neck.y,neck.z);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.clamp(MathHelper.wrapDegrees(bike.getYaw()-p.getHeadYaw()),-61,61)));m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.clamp(p.getPitch(),-40,45)));
            var top=new Vector3f(0,.39f,0);var bottom=new Vector3f();
            segment(m,v,light,skin,new Box(0,0,8,8,8,64),top,bottom,.40f,.40f);
            if(p.isPartVisible(PlayerModelPart.HAT))segment(m,v,light,skin,new Box(32,0,8,8,8,64),new Vector3f(0,.40f,0),bottom,.416f,.416f);
            var helmet=armor(p,EquipmentSlot.HEAD,0,0);
            if(helmet!=null)segment(m,v,light,helmet.tex,new Box(0,0,8,8,8,32),new Vector3f(0,.42f,0),bottom,.45f,.45f);
            m.pop();
        }
        for(int s:new int[]{-1,1}){
            boolean r=s>0;
            Vector3f grip=gripTarget(s,steer);
            if(r && (flags&1024)!=0)grip.add(-.024f,-.014f,-.012f);
            var shoulder=new Vector3f(neck).add(s*.225f,-.026f,0);
            var elbow=joint(shoulder,grip,.345f,.325f,new Vector3f(s*.43f,-.1f,.93f));
            int armWidth="slim".equals(p.getModel())?3:4;
            limb(m,v,light,p,skin,shoulder,elbow,grip,r?40:32,r?16:48,r?40:48,r?32:48,armWidth,armWidth*.040f,
                p.isPartVisible(r?PlayerModelPart.RIGHT_SLEEVE:PlayerModelPart.LEFT_SLEEVE),armor(p,EquipmentSlot.CHEST,40,16),null,fp);
            if(fp)continue;
            Vector3f foot=ankleTarget(s,crank);
            if(dab && (r?(flags&256)!=0:(flags&128)!=0)){foot.set(s*.31f,.052f,.12f);}
            if((flags&512)!=0){
                float progress=MathHelper.clamp(bike.mountTime(t)/.55f,0,1);
                float reach=MathHelper.clamp((progress-(r?.19f:.36f))/(r?.81f:.64f),0,1);
                float arch=(float)Math.sin(Math.PI*reach);
                Vector3f ground=new Vector3f(-.32f,.052f,.20f);
                foot=ground.lerp(foot,reach).add(r?-.14f*arch:0,(r?.94f:.10f)*arch,r?.27f*arch:0);
            }
            var hip=new Vector3f(hips).add(s*.091f,0,0);
            var knee=joint(hip,foot,.425f,.415f,new Vector3f(s*.18f,0,-1));
            limb(m,v,light,p,skin,hip,knee,foot,r?0:16,r?16:48, r?0:0,r?32:48,4,.164f,
                p.isPartVisible(r?PlayerModelPart.RIGHT_PANTS_LEG:PlayerModelPart.LEFT_PANTS_LEG),armor(p,EquipmentSlot.LEGS,0,16),armor(p,EquipmentSlot.FEET,0,16),false);
            var sole=new Vector3f(foot).add(0,-.044f,-.025f);
            var boot=armor(p,EquipmentSlot.FEET,0,16);
            segment(m,v,light,boot==null?skin:boot.tex,new Box(boot!=null||r?0:16,boot!=null||r?26:58,4,2,4,boot==null?64:32),
                new Vector3f(sole).add(0,.079f,0),sole,.172f,.223f);
        }
    }
    private void limb(MatrixStack m,VertexConsumerProvider v,int light,AbstractClientPlayerEntity p,Identifier skin,Vector3f a,Vector3f j,Vector3f b,
                      int u,int y,int outerU,int outerV,int width,float w,boolean overlay,Clothing upper,Clothing boot,boolean forearmOnly) {
        for(int i=forearmOnly?1:0;i<2;i++){
            Vector3f start=i==0?a:j,end=i==0?j:b;
            segment(m,v,light,skin,new Box(u,y+i*6,width,6,4,64),start,end,w,.17f);
            if(overlay)segment(m,v,light,skin,new Box(outerU,outerV+i*6,width,6,4,64),start,end,w+.008f,.178f);
            var layer=i==1&&boot!=null?boot:upper;
            if(layer!=null)segment(m,v,light,layer.tex,new Box(layer.u,layer.v+i*6,4,6,4,32),start,end,w+.042f,.215f);
        }
    }
}
