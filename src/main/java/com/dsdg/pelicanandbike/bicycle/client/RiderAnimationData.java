package com.dsdg.pelicanandbike.bicycle.client;

import com.google.gson.*;
import java.util.*;

final class RiderAnimationData {
    public record Vec(float x,float y,float z) {
        public static final Vec ZERO=new Vec(0,0,0);
        public Vec lerp(Vec b,float t){return new Vec(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t);}
    }
    public record Pose(Vec rotation,Vec position) {
        public static final Pose ZERO=new Pose(Vec.ZERO,Vec.ZERO);
    }
    public record Key(float time,Vec value) {}
    public record Track(List<Key> rotation,List<Key> position) {}
    public record Clip(float length,boolean loop,Map<String,Track> bones) {
        public Pose sample(String bone,float time){Track t=bones.get(bone);if(t==null)return Pose.ZERO;
            float at=Math.max(0,loop?time%length:Math.min(time,length));
            Vec r=channel(t.rotation,at),p=channel(t.position,at);
            return new Pose(r,p);}
    }
    public static Map<String,Clip> parse(JsonObject root) {
        Map<String,Clip> result=new HashMap<>();
        for(var entry:root.getAsJsonObject("animations").entrySet()) {
            JsonObject a=entry.getValue().getAsJsonObject();Map<String,Track> bones=new HashMap<>();
            for(var b:a.getAsJsonObject("bones").entrySet()) {JsonObject v=b.getValue().getAsJsonObject();bones.put(b.getKey(),new Track(keys(v.get("rotation")),keys(v.get("position"))));}
            result.put(entry.getKey(),new Clip(a.get("animation_length").getAsFloat(),a.has("loop")&&a.get("loop").getAsBoolean(),Map.copyOf(bones)));
        }
        return Map.copyOf(result);
    }
    private static List<Key> keys(JsonElement json) {
        if(json==null)return List.of();if(json.isJsonArray())return List.of(new Key(0,vector(json)));
        List<Key> keys=new ArrayList<>();for(var e:json.getAsJsonObject().entrySet())keys.add(new Key(Float.parseFloat(e.getKey()),vector(e.getValue())));
        keys.sort(Comparator.comparingDouble(Key::time));return List.copyOf(keys);
    }
    private static Vec vector(JsonElement json) {
        if(json.isJsonObject())json=json.getAsJsonObject().get("vector");JsonArray v=json.getAsJsonArray();
        return new Vec(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat());
    }
    private static Vec channel(List<Key> keys,float time) {
        if(keys.isEmpty())return Vec.ZERO;if(time<=keys.get(0).time)return keys.get(0).value;
        for(int i=1;i<keys.size();i++){Key b=keys.get(i),a=keys.get(i-1);if(time<=b.time)return a.value.lerp(b.value,(time-a.time)/Math.max(.00001F,b.time-a.time));}
        return keys.get(keys.size()-1).value;
    }
}
