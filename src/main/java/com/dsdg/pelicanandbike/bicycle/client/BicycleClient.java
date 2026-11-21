package com.dsdg.pelicanandbike.bicycle.client;

import com.dsdg.pelicanandbike.bicycle.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class BicycleClient implements ClientModInitializer {
    public record Fall(int side,float yaw,long start,long landed) {}
    private static final Map<UUID,Fall> falls=new HashMap<>();
    public record Handling(int mode,int bike,long start) {}
    private static final Map<UUID,Handling> handling=new HashMap<>();
    public static Handling handling(UUID id){return handling.get(id);}
    public static float cameraScale=.35f;
    private static boolean renderingWorld;
    private static boolean usingBicycleKeys;
    public static boolean usingBicycleKeys(){return usingBicycleKeys;}
    private static void useBicycleKeys(boolean mounted){
        if(usingBicycleKeys==mounted)return;
        usingBicycleKeys=mounted;
        var options=MinecraftClient.getInstance().options;
        var changing=new HashSet<String>();
        for(var key:options.allKeys)if(key.getCategory().equals("category.pelican_and_bike.bicycle")&&!key.isUnbound())
            changing.add(key.getBoundKeyTranslationKey());
        for(var key:options.allKeys)if(changing.contains(key.getBoundKeyTranslationKey())){
            key.setPressed(false);
            while(key.wasPressed()){ /* discard presses from the previous context */ }
        }
    }
    public static boolean renderingWorld(){return renderingWorld;}
    private static KeyBinding front,rear,up,down,stand,back,forward,camera;
    private static int sequence;
    public static Fall fall(UUID id) {return falls.get(id);}
    private static KeyBinding bind(String name,int key){return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.pelican_and_bike.bicycle."+name,InputUtil.Type.KEYSYM,key,"category.pelican_and_bike.bicycle"));}
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register((entity,world)->{
            falls.remove(entity.getUuid());handling.remove(entity.getUuid());
        });
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(ctx->renderingWorld=true);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(ctx->renderingWorld=false);
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES).registerReloadListener(new RiderClips());
        front=bind("front",GLFW.GLFW_KEY_R);rear=bind("rear",GLFW.GLFW_KEY_F);
        up=bind("up",GLFW.GLFW_KEY_RIGHT_BRACKET);down=bind("down",GLFW.GLFW_KEY_LEFT_BRACKET);
        stand=bind("stand",GLFW.GLFW_KEY_LEFT_CONTROL);back=bind("back",GLFW.GLFW_KEY_X);forward=bind("forward",GLFW.GLFW_KEY_C);
        camera=bind("camera",GLFW.GLFW_KEY_UNKNOWN);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(c->KeyBinding.updateKeysByCode());
        EntityRendererRegistry.register(BicycleModule.TYPE,BicycleRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(BicycleModule.FALL,(c,h,buf,sender)->{
            int id=buf.readVarInt(),side=buf.readByte();float yaw=buf.readFloat();boolean landed=buf.readBoolean();
            c.execute(()->{if(c.world==null)return;var who=c.world.getEntityById(id);if(who==null)return;
                long now=c.world.getTime();var old=falls.get(who.getUuid());
                falls.put(who.getUuid(),new Fall(side,yaw,landed&&old!=null?old.start:now,landed?now:-1));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(BicycleModule.ACTION,(c,h,buf,sender)->{
            int who=buf.readVarInt(),bike=buf.readVarInt(),mode=buf.readByte();
            c.execute(()->{if(c.world==null)return;var p=c.world.getEntityById(who);if(p==null)return;
                if(mode==0)handling.remove(p.getUuid());else handling.put(p.getUuid(),new Handling(mode,bike,c.world.getTime()));
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{falls.clear();handling.clear();sequence=0;useBicycleKeys(false);});
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private boolean upHeld,downHeld;
    private int previousBike=-1;
    private void tick(MinecraftClient c){
        if(c.world==null||c.player==null)return;
        useBicycleKeys(c.player.getVehicle() instanceof BicycleEntity);
        long now=c.world.getTime();
        falls.entrySet().removeIf(e->{var p=c.world.getPlayerByUuid(e.getKey());var f=e.getValue();
            return p==null||!p.isAlive()||p.hasVehicle()&&now-f.start>4||now-f.start>180||f.landed>=0&&now-f.landed>20;});
        handling.entrySet().removeIf(e->{var h=e.getValue();var b=c.world.getEntityById(h.bike);
            if(!(b instanceof BicycleEntity bike)||bike.hasPassengers())return true;
            return h.mode==BicycleModule.PUSH ? now-h.start>3 && (bike.flags()&64)==0 : now-h.start>20;});
        if(!(c.player.getVehicle() instanceof BicycleEntity bike)){previousBike=-1;upHeld=downHeld=false;return;}
        while(camera.wasPressed())cameraScale=cameraScale==0?.35f:cameraScale<.5f?.75f:0;
        if(previousBike!=bike.getId()){
            c.player.sendMessage(Text.translatable("hint.pelican_and_bike.bicycle"),true);previousBike=bike.getId();
            if(c.options.getPerspective().isFirstPerson() && Math.abs(c.player.getPitch())<12)c.player.setPitch(29);
        }
        int mask=0;
        KeyBinding[] keys={c.options.forwardKey,c.options.leftKey,c.options.rightKey,front,rear,stand,back,forward};
        for(int i=0;i<keys.length;i++) if(BicycleInput.held(c,keys[i])) mask|=1<<i;
        if(BicycleInput.held(c,c.options.backKey)) mask|=BicycleDynamics.FRONT|BicycleDynamics.REAR;
        boolean u=BicycleInput.held(c,up),d=BicycleInput.held(c,down);
        int shift=(u&&!upHeld?1:0)-(d&&!downHeld?1:0);upHeld=u;downHeld=d;
        var buf=PacketByteBufs.create();buf.writeVarInt(bike.getId());buf.writeVarInt(bike.rideSession());
        buf.writeVarInt(++sequence);buf.writeByte(mask);buf.writeByte(shift);
        ClientPlayNetworking.send(BicycleModule.INPUT,buf);
    }
}
