package com.dsdg.pelicanandbike.bicycle.client;

import com.dsdg.pelicanandbike.bicycle.BicycleModule;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public final class RiderClips implements SimpleSynchronousResourceReloadListener {
    private static Map<String,RiderAnimationData.Clip> clips=Map.of();
    public Identifier getFabricId(){return BicycleModule.id("rider_clips");}
    public void reload(ResourceManager manager) {
        try(var r=new InputStreamReader(manager.getResourceOrThrow(BicycleModule.id("animations/bicycle_rider.animation.json")).getInputStream(),StandardCharsets.UTF_8)){
            clips=Map.copyOf(RiderAnimationData.parse(JsonParser.parseReader(r).getAsJsonObject()));
        } catch(java.io.IOException ex){throw new IllegalStateException("Loading bicycle rider clips",ex);}
    }
    static RiderAnimationData.Pose sample(String action,String bone,float time){
        var clip=clips.get("animation.rider."+action);
        return clip==null?RiderAnimationData.Pose.ZERO:clip.sample(bone,Math.max(0,time));
    }
    public static float fallDegrees(int side,float time) {
        String action=side==2?"over_bars":(Math.abs(side)==3?"tip_":"slide_")+(side<0?"left":"right");
        var rot=sample(action,"fall_root",time).rotation();
        return side==2?rot.x():rot.z();
    }
}
