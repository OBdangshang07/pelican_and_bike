package com.dsdg.pelicanandbike.pelican.client;

import com.google.gson.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PelicanContactTest {
    private static Map<String,JsonObject> bones(String model) throws Exception {
        var path=Path.of("src/main/resources/assets/pelican_and_bike/geo",model+".geo.json");
        var geo=JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Map<String,JsonObject> result=new HashMap<>();
        for(var entry:geo.getAsJsonArray("bones")){
            var bone=entry.getAsJsonObject();result.put(bone.get("name").getAsString(),bone);
        }
        return result;
    }
    private static Vector3f vector(JsonObject object,String key){
        var a=object.getAsJsonArray(key);
        return new Vector3f(-a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());
    }
    private static Vector3f pivot(JsonObject o){return vector(o,"pivot");}
    @Test void exportedWebAndCarpalJointTouchOriginalBike() throws Exception {
        var bike=bones("bicycle");var bird=bones("pelican");
        var crank=pivot(bike.get("crank"));var stem=pivot(bike.get("steer"));
        float footError=0,wristError=0;int samples=0;
        for(int side:new int[]{-1,1}){
            String tag=side<0?"l":"r";
            var pedal= bike.get("pedal_"+tag);var axle=pivot(pedal);
            var rail=pedal.getAsJsonArray("cubes").get(3).getAsJsonObject();
            var railTop=vector(rail,"origin").add(vector(rail,"size").mul(.5f,1,.5f)).sub(axle);
            var pad=bird.get("digit_"+tag+"2").getAsJsonArray("cubes").get(2).getAsJsonObject();
            var padRest=vector(pad,"origin").add(vector(pad,"size").mul(.5f,0,.5f));
            var footRest=pivot(bird.get("foot_"+tag));
            var shoulder=pivot(bird.get("wing_"+tag));var elbow=pivot(bird.get("elbow_"+tag));var wrist=pivot(bird.get("wrist_"+tag));
            var lever= bike.get(side<0?"lever_left":"lever_right");var leverHinge=pivot(lever);
            var leverContact=pivot(lever.getAsJsonArray("cubes").get(0).getAsJsonObject());
            Vector3f grip=null;
            for(var e:bike.get("steer").getAsJsonArray("cubes")){
                var c=e.getAsJsonObject();
                if(c.has("pivot")&&pivot(c).x*side>4.5f&&vector(c,"size").y>2)grip=pivot(c);
            }
            assertNotNull(grip);
            for(int i=0;i<360;i++)for(int brake=0;brake<=1;brake++){
                float phase=(float)Math.toRadians(i),steer=.62f*(float)Math.sin(phase);
                float lift=7.05f+.66f*(float)Math.cos(2*phase),aft=.48f*(float)Math.cos(phase);
                var sole=PelicanContact.pedal(side,phase).add(-side*.0415f,.1365f,.23355f).sub(footRest).add(padRest);
                var platform=new Vector3f(axle).sub(crank).rotateX(-phase).add(crank).add(railTop);
                var reach=PelicanContact.reach(side,PelicanContact.grip(side,steer,brake),lift,aft);
                var actual=new Vector3f(wrist).sub(elbow).rotate(reach.elbow()).add(elbow).sub(shoulder)
                        .rotate(reach.shoulder()).add(shoulder).add(0,lift,aft);
                var contact=brake==0?new Vector3f(grip):new Vector3f(leverContact).sub(leverHinge).rotateY(side*.34f).add(leverHinge);
                contact.sub(stem).rotateY(-steer).add(stem);
                float lean=.52f*(float)Math.sin(phase),pitch=.11f*(float)Math.cos(phase);
                footError=Math.max(footError,sole.rotateX(pitch).rotateZ(lean).distance(platform.rotateX(pitch).rotateZ(lean))/16);
                wristError=Math.max(wristError,actual.rotateX(pitch).rotateZ(lean).distance(contact.rotateX(pitch).rotateZ(lean))/16);
                samples++;
            }
        }
        System.out.printf(Locale.ROOT,"%d contacts per limb type, sole max %.7f blocks, carpus max %.7f blocks%n",samples,footError,wristError);
        assertTrue(footError<=.02f);assertTrue(wristError<=.02f);
    }
}
