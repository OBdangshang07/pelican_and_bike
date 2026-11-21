package com.dsdg.pelicanandbike.bicycle;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class BicycleModule implements ModInitializer {
    public static Identifier id(String path) { return new Identifier("pelican_and_bike",path); }
    public static final Identifier INPUT=id("bicycle_input"), FALL=id("bicycle_fall"), ACTION=id("bicycle_action");
    public static final int PUSH=1, LIFT=2, STEP_OFF=3;
    public static final EntityType<BicycleEntity> TYPE=Registry.register(Registries.ENTITY_TYPE,id("bicycle"),
        FabricEntityTypeBuilder.create(SpawnGroup.MISC,BicycleEntity::new).dimensions(EntityDimensions.fixed(.58F,1.02F))
            .trackRangeBlocks(80).trackedUpdateRate(1).build());
    public static final Item ITEM=Registry.register(Registries.ITEM,id("bicycle"),new Item(new Item.Settings().maxCount(1)) {
        @Override public ActionResult useOnBlock(ItemUsageContext ctx) {
            if (ctx.getWorld().isClient) return ActionResult.SUCCESS;
            if (!(ctx.getPlayer() instanceof ServerPlayerEntity p)) return ActionResult.FAIL;
            var world=p.getServerWorld();
            Vec3d at=ctx.getHitPos().add(Vec3d.of(ctx.getSide().getVector()).multiply(.025));
            if (ctx.getSide().getAxis().isHorizontal()) at=at.add(Vec3d.of(ctx.getSide().getVector()).multiply(.40));
            var bike=TYPE.create(world);
            if (bike==null) return ActionResult.FAIL;
            bike.refreshPositionAndAngles(at.x,at.y,at.z,p.getYaw(),0);
            if (!world.getWorldBorder().contains(bike.getBlockPos()) || !world.isSpaceEmpty(bike,bike.getBoundingBox())
                    || !world.getOtherEntities(p,bike.getBoundingBox(),e->e.isAlive()&&e.canHit()).isEmpty() || !world.spawnEntity(bike)) return ActionResult.FAIL;
            if (!p.isCreative()) ctx.getStack().decrement(1);
            return ActionResult.CONSUME;
        }
    });
    private static final class Impact {
        final ServerWorld world;
        final long start;
        final int side;
        final float yaw;
        final double normalSpeed;
        double previousY,downwardSpeed;
        float vanillaFallDamage;
        Impact(ServerPlayerEntity player,int side,float yaw,double normalSpeed){
            world=player.getServerWorld();start=world.getTime();previousY=player.getY();
            this.side=side;this.yaw=yaw;this.normalSpeed=normalSpeed;
        }
    }
    private static final Map<UUID,Impact> FALLING=new HashMap<>();
    @Override public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries->entries.add(ITEM));
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,p,handler,buf,responder)->{
            if(buf.readableBytes()<5 || buf.readableBytes()>17)return;
            final int bike,session,seq,mask,shift;
            try {
                bike=buf.readVarInt();session=buf.readVarInt();seq=buf.readVarInt();mask=buf.readUnsignedByte();shift=buf.readByte();
            } catch(RuntimeException invalid){return;}
            if(buf.isReadable()||session<0||seq<0||Math.abs(shift)>1)return;
            server.execute(()->{ if(p.getVehicle() instanceof BicycleEntity b && b.getId()==bike) b.accept(p,session,seq,mask,shift); });
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((living,source,amount)->{
            var impact=FALLING.get(living.getUuid());
            if(impact==null || impact.world!=living.getWorld() || !source.isOf(net.minecraft.entity.damage.DamageTypes.FALL))return true;
            impact.vanillaFallDamage=Math.max(impact.vanillaFallDamage,amount);
            return false;
        });
        ServerTickEvents.END_SERVER_TICK.register(server->FALLING.entrySet().removeIf(entry->{
            var p=server.getPlayerManager().getPlayer(entry.getKey()); var impact=entry.getValue();
            if (p==null || !p.isAlive() || p.hasVehicle() || p.getWorld()!=impact.world) return true;
            long age=p.getWorld().getTime()-impact.start;
            impact.downwardSpeed=Math.max(impact.downwardSpeed,Math.max(0,impact.previousY-p.getY())*20);
            impact.previousY=p.getY();
            if (age>2 && (p.isOnGround() || p.isTouchingWater())) {
                double energy=impact.normalSpeed*impact.normalSpeed+impact.downwardSpeed*impact.downwardSpeed;
                float damage=Math.max(impact.vanillaFallDamage,(float)Math.min(18,Math.max(0,energy-12)/12));
                if (!p.isTouchingWater() && damage>.05f) p.damage(p.getDamageSources().flyIntoWall(),damage);
                send(p,impact.side,impact.yaw,true);
                return true;
            }
            return age>160;
        }));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s->FALLING.clear());
    }
    public static void fall(ServerPlayerEntity p,int side,float yaw,double speed) {
        FALLING.put(p.getUuid(),new Impact(p,side,yaw,side==2?speed:speed*.38));
        send(p,side,yaw,false);
    }
    public static void action(ServerPlayerEntity player,BicycleEntity bike,int mode) {
        var data=PacketByteBufs.create();
        data.writeVarInt(player.getId());data.writeVarInt(bike.getId());data.writeByte(mode);
        var viewers=new HashSet<>(PlayerLookup.tracking(player));viewers.add(player);
        for(var viewer:viewers)ServerPlayNetworking.send(viewer,ACTION,data);
    }
    private static void send(ServerPlayerEntity p,int side,float yaw,boolean landed) {
        PacketByteBuf data=PacketByteBufs.create();
        data.writeVarInt(p.getId()); data.writeByte(side); data.writeFloat(yaw); data.writeBoolean(landed);
        var targets=new HashSet<>(PlayerLookup.tracking(p)); targets.add(p);
        for (var target:targets) ServerPlayNetworking.send(target,FALL,data);
    }
}
