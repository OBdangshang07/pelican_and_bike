package com.dsdg.pelicanandbike.bicycle.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiderContactTest {
    @Test void mountedRiderResolvesVanillaAndNamespacedArmorLayers(){
        var legs=net.minecraft.entity.EquipmentSlot.LEGS;
        var head=net.minecraft.entity.EquipmentSlot.HEAD;
        org.junit.jupiter.api.Assertions.assertEquals("minecraft:textures/models/armor/iron_layer_1.png",RiderMesh.armorTexture("iron",head).toString());
        org.junit.jupiter.api.Assertions.assertEquals("example:textures/models/armor/test_layer_1.png",RiderMesh.armorTexture("example:test",head).toString());
    }
    private static Vector3f point(JsonArray array){
        // Bedrock negates X on export from Blockbench. The renderer uses blocks.
        return new Vector3f(-array.get(0).getAsFloat(),array.get(1).getAsFloat(),array.get(2).getAsFloat()).div(16);
    }
    @Test void verifyContactsAgainstExportedAxlesAndGripGeometry() throws Exception {
        var dir=Path.of("src/main/resources/assets/pelican_and_bike");
        var root=JsonParser.parseString(Files.readString(dir.resolve("geo/bicycle.geo.json"))).getAsJsonObject();
        Map<String,JsonObject> bones=new HashMap<>();
        for(var element:root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")){
            var bone=element.getAsJsonObject();bones.put(bone.get("name").getAsString(),bone);
        }
        Vector3f axle=point(bones.get("crank").getAsJsonArray("pivot")),stem=point(bones.get("steer").getAsJsonArray("pivot"));
        float maxSole=0,maxHand=0;
        for(int s:new int[]{-1,1}){
            var pedal=point(bones.get(s>0?"pedal_r":"pedal_l").getAsJsonArray("pivot"));
            var bar=bones.get("steer").getAsJsonArray("cubes");
            // Independent extraction of the long cylindrical grip segment on the matching side.
            Vector3f center=null;
            for(var e:bar){
                var cube=e.getAsJsonObject();if(!cube.has("pivot"))continue;
                var p=point(cube.getAsJsonArray("pivot"));var size=cube.getAsJsonArray("size");
                if(s*p.x>.27&&s*p.x<.32&&p.y>.95&&size.get(1).getAsDouble()>2)center=p;
            }
            assertNotNull(center);
            for(int f=0;f<360;f++){
                float phase=(float)Math.toRadians(f),steer=.55f*(float)Math.sin(phase);
                var platform=new Vector3f(pedal).sub(axle).rotateX(-phase).add(axle);
                var sole=RiderMesh.ankleTarget(s,phase).add(0,-.044f,-.025f);
                // Compare the shoe centreline to the same point on the top rail.
                maxSole=Math.max(maxSole,sole.distance(platform.add(0,.105f/16,-.025f)));
                var grip=new Vector3f(center).sub(stem).rotateY(-steer).add(stem);
                maxHand=Math.max(maxHand,grip.distance(RiderMesh.gripTarget(s,steer)));
            }
        }
        System.out.printf("720 contact samples: sole %.5f blocks; grip %.5f blocks%n",maxSole,maxHand);
        assertTrue(maxSole<.03&&maxHand<.03);
    }
}
