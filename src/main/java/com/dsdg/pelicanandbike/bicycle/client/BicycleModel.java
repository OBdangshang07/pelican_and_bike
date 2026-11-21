package com.dsdg.pelicanandbike.bicycle.client;

import com.dsdg.pelicanandbike.bicycle.*;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/** Animation channels are sampled in radians in the same bike-local frame as the rider. */
public final class BicycleModel extends GeoModel<BicycleEntity> {
    private static final Identifier GEO=BicycleModule.id("geo/bicycle.geo.json"), PNG=BicycleModule.id("textures/entity/bicycle/bicycle.png"), ANIM=BicycleModule.id("animations/bicycle.animation.json");
    private static final Identifier DISTANT=BicycleModule.id("geo/bicycle_lod.geo.json");
    private static boolean distant(BicycleEntity bike){
        var camera=net.minecraft.client.MinecraftClient.getInstance().getCameraEntity();
        return camera!=null && camera.squaredDistanceTo(bike)>24*24;
    }
    @Override public Identifier getModelResource(BicycleEntity bike){return distant(bike)?DISTANT:GEO;}
    @Override public Identifier getTextureResource(BicycleEntity bike){return PNG;}
    @Override public Identifier getAnimationResource(BicycleEntity bike){return ANIM;}
    private void rotate(String name,float x,float y,float z) {
        var b=getAnimationProcessor().getBone(name);
        if(b!=null) { b.setRotX(x); b.setRotY(y); b.setRotZ(z); }
    }
    private void offset(String name,float x,float y,float z) {
        var b=getAnimationProcessor().getBone(name);
        if(b!=null) { b.setPosX(-x);b.setPosY(y);b.setPosZ(z); }
    }
    static final float[][] CHAIN={{1.3f,6.09f,.98f},{.98f,7.08f,8.96f},{.98f,6.66f,9.86f},{.98f,5.40f,10.02f},{1.28f,4.53f,9.55f},{1.30f,3.20f,8.97f},{1.30f,2.90f,8.52f},{1.30f,3.83f,.98f},{1.30f,4.16f,.15f},{1.30f,4.96f,-.15f},{1.30f,5.76f,.15f}};
    private static final float[] ARCS=new float[CHAIN.length+1];
    private static final int LINKS=60;
    private static final Vector3f[] REST=new Vector3f[LINKS];
    private static final String[] NAMES=new String[LINKS];
    static {
        for(int i=0;i<CHAIN.length;i++) {
            float[] a=CHAIN[i], b=CHAIN[(i+1)%CHAIN.length];
            ARCS[i+1]=ARCS[i]+Vector3f.distance(a[0],a[1],a[2],b[0],b[1],b[2]);
        }
        for(int i=0;i<LINKS;i++){REST[i]=point(i/(float)LINKS,4,new Vector3f()); NAMES[i]="link_"+i;}
    }
    private static Vector3f point(float phase,int gear,Vector3f out){
        float at=(phase-(float)Math.floor(phase))*ARCS[CHAIN.length];
        int i=0;while(i+1<CHAIN.length && at>ARCS[i+1]) i++;
        float[] a=CHAIN[i], b=CHAIN[(i+1)%CHAIN.length]; float t=(at-ARCS[i])/(ARCS[i+1]-ARCS[i]);
        out.set(a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t);
        float weight=Math.max(0,Math.min(1,(out.z-1)/7.5f));
        out.x+=(gear-4)*.088f*weight;
        out.y-=(gear-4)*.106f*weight*Math.max(0,out.y-5.92f);
        return out;
    }
    private final Vector3f point=new Vector3f(), next=new Vector3f();
    @Override public void setCustomAnimations(BicycleEntity b,long id,AnimationState<BicycleEntity> state) {
        super.setCustomAnimations(b,id,state);
        float t=state.getPartialTick(), steer=b.sample(0,1,t), crank=b.sample(1,2,t);
        rotate("chassis",b.sample(0,2,t),0,-b.sample(0,0,t));
        rotate("steer",0,-steer,0);
        offset("fork",0,b.sample(2,2,t)*16,b.sample(2,2,t)*5);
        rotate("front_wheel",-b.sample(1,0,t),0,0);rotate("rear_wheel",-b.sample(1,1,t),0,0);
        rotate("crank",-crank,0,0);rotate("pedal_l",crank,0,0);rotate("pedal_r",crank,0,0);
        int gear=b.flags()&15;
        rotate("freehub",-b.sample(4,0,t),0,0);
        rotate("lever_left",0,-b.sample(2,0,t)*.34f,0);rotate("lever_right",0,b.sample(2,1,t)*.34f,0);
        rotate("trigger",0,0,(b.flags()&1024)!=0?-.28f:0);
        rotate("hose_r",0,steer*.30f,0);rotate("hose_l",0,steer*.30f,0);
        offset("derailleur",(gear-4)*.088f,0,0);rotate("cage",(gear-4)*.05f,0,0);
        rotate("kickstand",b.hasPassengers()||(b.flags()&(32|64))!=0?-1.34f:0,0,0);
        if(distant(b))return;
        // Repeated pairs of plates can wrap by two links without changing their exterior.
        float travel=-crank/(float)(Math.PI*2)*32;
        float phase=(travel-(float)Math.floor(travel/2)*2)/LINKS;
        for(int i=0;i<LINKS;i++){
            point(i/(float)LINKS+phase,gear,point);point((i+1)/(float)LINKS+phase,gear,next);
            var rest=REST[i];var restNext=REST[(i+1)%LINKS];
            offset(NAMES[i],point.x-rest.x,point.y-rest.y,point.z-rest.z);
            float a=(float)Math.atan2(restNext.z-rest.z,restNext.y-rest.y);
            float c=(float)Math.atan2(next.z-point.z,next.y-point.y);
            rotate(NAMES[i],c-a,0,0);
        }
    }
}
