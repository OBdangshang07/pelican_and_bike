package com.dsdg.pelicanandbike.bicycle;

import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class BicycleEntity extends Entity implements GeoEntity {
    private static TrackedData<Vector3f> vector() { return DataTracker.registerData(BicycleEntity.class,TrackedDataHandlerRegistry.VECTOR3F); }
    private static final TrackedData<Vector3f> ANGLES = vector(), PHASE = vector(), CONTROLS = vector(), MOTION = vector(), RIDER = vector();
    private static final java.util.List<TrackedData<Vector3f>> CHANNELS=java.util.List.of(ANGLES,PHASE,CONTROLS,MOTION,RIDER);
    private static final TrackedData<Integer> FLAGS = DataTracker.registerData(BicycleEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public final BicycleDynamics dynamics = new BicycleDynamics();
    private int input, lastInput, lastSequence = -1, steeringTicks, riseTicks, shiftTicks, session;
    private int fallSide;
    private int fallTicks;
    private double impactSpeed, impactFork, landingPulse;
    private boolean separating;
    private PlayerEntity pushing;
    private Vec3d interpolationTarget;
    private int interpolationTicks;
    private float targetYaw;
    private double vertical, tipped;
    private final Vector3f[] old = new Vector3f[CHANNELS.size()], current = new Vector3f[CHANNELS.size()];
    private int predictionTicks;

    public BicycleEntity(EntityType<? extends BicycleEntity> type, World world) {
        super(type, world);
        for (int i=0;i<CHANNELS.size();i++) { old[i]=new Vector3f(); current[i]=new Vector3f(); }
    }
    @Override protected void initDataTracker() {
        for (var key : CHANNELS) dataTracker.startTracking(key,new Vector3f());
        dataTracker.startTracking(FLAGS,0);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public boolean canHit() { return !isRemoved(); }
    @Override protected boolean canAddPassenger(Entity e) { return e instanceof PlayerEntity && !hasPassengers() && fallSide == 0; }
    @Override public double getMountedHeightOffset() { return .08; }
    @Override public boolean shouldDismountUnderwater() { return true; }
    public int flags() { return dataTracker.get(FLAGS); }
    private int clientMountTicks;
    public float mountTime(float delta) { return (clientMountTicks+delta)/20f; }
    public int rideSession() { return flags() >>> 16; }
    public float sample(int group, int axis, float t) {
        float a=old[group].get(axis), b=current[group].get(axis);
        if (group==1) return a+t*(float)Math.IEEEremainder(b-a,8*Math.PI);
        if (group==4 && axis==0) return a + t*(float)Math.atan2(Math.sin(b-a),Math.cos(b-a));
        return MathHelper.lerp(t,a,b);
    }
    public void accept(ServerPlayerEntity player, int epoch, int sequence, int controls, int shift) {
        if (getFirstPassenger()!=player || epoch!=session || sequence<=lastSequence) return;
        lastSequence=sequence;
        lastInput=age;
        input=controls & 255;
        if (shift!=0 && shiftTicks==0) { dynamics.shift(shift); shiftTicks=4; }
    }
    @Override protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!getWorld().isClient) {
            session=session % 32767 + 1; lastSequence=-1; input=0; lastInput=age; steeringTicks=0; pushing=null; publish();
        }
    }
    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!getWorld().isClient) {
            input=0;
            if (!separating && passenger instanceof ServerPlayerEntity p && p.isAlive() && Math.abs(dynamics.speed)>3) {
                markFall(dynamics.lean>0?1:-1);
                release(p,fallSide,Math.abs(dynamics.speed));
            } else if (!separating && passenger instanceof ServerPlayerEntity p && p.isAlive()) {
                BicycleModule.action(p,this,BicycleModule.STEP_OFF);
            }
        }
    }
    @Override public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        for (double side : new double[]{fallSide>=0 ? .7 : -.7,fallSide>=0 ? -.7 : .7,0}) {
            Vec3d at = local(0,side).add(0,.08,0);
            if (getWorld().isSpaceEmpty(passenger,passenger.getDimensions(EntityPose.STANDING).getBoxAt(at))) return at;
        }
        return super.updatePassengerForDismount(passenger);
    }
    @Override protected void updatePassengerPosition(Entity rider,PositionUpdater updater) {
        if (!hasPassenger(rider)) return;
        Vec3d at=local(-.12,0);
        updater.accept(rider,at.x,at.y+.08,at.z);
        rider.fallDistance=0;
        if (rider instanceof LivingEntity living) living.setBodyYaw(getYaw());
    }
    @Override public ActionResult interact(PlayerEntity player, Hand hand) {
        if (hand!=Hand.MAIN_HAND || hasPassengers()) return ActionResult.PASS;
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity p)) return ActionResult.FAIL;
        if (fallSide!=0) {
            if (Math.abs(dynamics.speed)>.8) return ActionResult.FAIL;
            fallSide=0; riseTicks=12; dynamics.lean=0;dynamics.instability=0;
            BicycleModule.action(p,this,BicycleModule.LIFT);return ActionResult.CONSUME;
        }
        if (riseTicks>0) return ActionResult.CONSUME;
        if (pushing!=null && pushing!=player) return ActionResult.FAIL;
        if (player.isSneaking()) {
            pushing=pushing==player?null:player;BicycleModule.action(p,this,pushing!=null?BicycleModule.PUSH:0);return ActionResult.CONSUME;
        }
        pushing=null;
        return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.FAIL;
    }
    @Override public boolean damage(DamageSource source,float amount) {
        if (getWorld().isClient || hasPassengers() || !(source.getAttacker() instanceof PlayerEntity p) ||
            !(p instanceof ServerPlayerEntity) || !p.isSneaking() || Math.abs(dynamics.speed)>.3) return false;
        if (!p.isCreative()) dropItem(BicycleModule.ITEM);
        discard();
        return true;
    }
    private Vec3d local(double front,double side) {
        double yaw=Math.toRadians(getYaw());
        return getPos().add(-Math.sin(yaw)*front-Math.cos(yaw)*side,0,Math.cos(yaw)*front-Math.sin(yaw)*side);
    }
    private record Probe(double y,BicycleDynamics.Ground ground) {}
    private Probe probe(Vec3d at) {
        Vec3d top=at.add(0,.66,0);
        BlockHitResult hit=getWorld().raycast(new RaycastContext(top,at.add(0,-.62,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,this));
        if (hit.getType()==HitResult.Type.MISS) return new Probe(at.y-.64,new BicycleDynamics.Ground(BicycleDynamics.Surface.HARD,false));
        BlockPos pos=hit.getBlockPos();
        Block block=getWorld().getBlockState(pos).getBlock();
        var surface=BicycleDynamics.Surface.HARD;
        if (block.getSlipperiness()>=.95F) surface=BicycleDynamics.Surface.ICE;
        else if (block==Blocks.GRAVEL || block instanceof FallingBlock) surface=BicycleDynamics.Surface.GRAVEL;
        else if (block==Blocks.GRASS_BLOCK || block==Blocks.DIRT || block==Blocks.DIRT_PATH || block==Blocks.MUD) surface=BicycleDynamics.Surface.SOIL;
        else if (getWorld().hasRain(pos.up()) || !getWorld().getFluidState(pos.up()).isEmpty()) surface=BicycleDynamics.Surface.WET;
        return new Probe(hit.getPos().y,new BicycleDynamics.Ground(surface,hit.getPos().y>=at.y-.17));
    }
    private void markFall(int side) {
        fallSide=side; pushing=null; input=0; dynamics.instability=0;
        fallTicks=0;impactSpeed=Math.abs(dynamics.speed);
    }
    private void release(ServerPlayerEntity p,int direction,double impact) {
        double yaw=Math.toRadians(getYaw()), velocity=dynamics.speed/20;
        double lateral=direction==2?0:direction*Math.min(.20,.045+Math.abs(velocity)*.3);
        p.setVelocity(-Math.sin(yaw)*velocity-Math.cos(yaw)*lateral,direction==2?.25:.10,Math.cos(yaw)*velocity-Math.sin(yaw)*lateral);
        p.velocityModified=true;
        int animation=direction!=2 && Math.abs(dynamics.speed)<1.3 ? direction*3 : direction;
        BicycleModule.fall(p,animation,getYaw(),impact);
    }
    private void eject(int side) {
        eject(side,Math.abs(dynamics.speed));
    }
    private void eject(int side,double impact) {
        if (fallSide!=0) return;
        markFall(side);
        if (getFirstPassenger() instanceof ServerPlayerEntity p) {
            separating=true;
            try { p.stopRiding(); } finally { separating=false; }
            release(p,side,impact);
        }
    }
    @Override public void updateTrackedPositionAndAngles(double x,double y,double z,float yaw,float pitch,int steps,boolean interpolate) {
        interpolationTarget=new Vec3d(x,y,z); interpolationTicks=2; predictionTicks=0; targetYaw=yaw;
    }
    @Override public void tick() {
        super.tick();
        if (getWorld().isClient) {
            clientMountTicks=hasPassengers()?Math.min(20,clientMountTicks+1):0;
            if (interpolationTicks>0) {
                setYaw(getYaw()+MathHelper.wrapDegrees(targetYaw-getYaw())/interpolationTicks);
                setPosition(getPos().lerp(interpolationTarget,1.0/interpolationTicks--));
            } else if(hasPassengers() && predictionTicks++<2 && Math.abs(current[3].x)>.3) {
                double dir=Math.toRadians(getYaw())-current[3].y, distance=current[3].x*.05;
                Vec3d delta=new Vec3d(-Math.sin(dir)*distance,0,Math.cos(dir)*distance);
                if(getWorld().isSpaceEmpty(this,getBoundingBox().offset(delta)))setPosition(getPos().add(delta));
            }
            int i=0;
            for (var key : CHANNELS) { old[i].set(current[i]); current[i].set(dataTracker.get(key)); i++; }
            return;
        }
        if (shiftTicks>0) shiftTicks--;
        if (riseTicks>0) riseTicks--;
        if (fallSide!=0) fallTicks=Math.min(120,fallTicks+1);
        impactFork*=.74;landingPulse*=.82;
        if (age-lastInput>6) input=0;
        if (pushing!=null && (!pushing.isAlive() || pushing.isRemoved() || pushing.getWorld()!=getWorld() || squaredDistanceTo(pushing)>10 || pushing.hasVehicle())) pushing=null;
        boolean riding=hasPassengers();
        steeringTicks=riding?Math.min(20,steeringTicks+1):0;
        if (!riding && pushing==null && Math.abs(dynamics.speed)<.015 && Math.abs(dynamics.frontRate)+Math.abs(dynamics.rearRate)<.01 && (isOnGround()||Math.abs(vertical)<.025) && age%10!=0 && riseTicks==0 && Math.abs(tipped-restLean())<.01) return;
        if (pushing!=null) {
            Vec3d wanted=pushing.getPos().add(new Vec3d(.64,0,.25).rotateY((float)-Math.toRadians(pushing.getYaw())));
            Vec3d diff=wanted.subtract(getPos()).multiply(1,0,1);
            setYaw(MathHelper.lerpAngleDegrees(.42f,getYaw(),pushing.getYaw()));
            move(MovementType.SELF,diff.multiply(Math.min(1,.24/Math.max(.01,diff.length()))));
            dynamics.frontPhase+=Math.min(.24,diff.length())/BicycleDynamics.RADIUS;
            dynamics.rearPhase=dynamics.frontPhase; dynamics.speed=0;
        }
        double dt=BicycleDynamics.DT/3;
        for (int n=0;n<3;n++) {
            Probe f=probe(local(.56,0)), r=probe(local(-.56,0));
            double slope=Math.atan2(f.y-r.y,BicycleDynamics.WHEELBASE);
            boolean foot=f.ground.contact() && r.ground.contact();
            if (riding && Math.abs(dynamics.speed)<1) {
                var left=probe(local(-.12,-.30)); var right=probe(local(-.12,.30));
                foot=left.ground.contact() || right.ground.contact();
                supportSide=right.ground.contact() && (!left.ground.contact()||dynamics.lean>.08)?1:left.ground.contact()?-1:0;
                if(supportSide!=0)dynamics.supportSide=supportSide;
            } else supportSide=0;
            // Once the frame rests on its side the axles can still spin freely.
            boolean wheelsClear=fallSide!=0 && Math.abs(tipped)>.92;
            var frontWheel=wheelsClear?new BicycleDynamics.Ground(f.ground.surface(),false):f.ground;
            var rearWheel=wheelsClear?new BicycleDynamics.Ground(r.ground.surface(),false):r.ground;
            var fall=dynamics.step(riding?input:0,frontWheel,rearWheel,slope,foot||!riding,dt);
            if(!riding) {
                double friction=fallSide!=0?3.9:Math.abs(dynamics.speed)<.38?9.81:.32;
                dynamics.speed=Math.copySign(Math.max(0,Math.abs(dynamics.speed)-friction*dt),dynamics.speed);
            }
            if (riding && fall!=BicycleDynamics.Fall.NONE) { eject(fall==BicycleDynamics.Fall.OVER_BARS?2:fall==BicycleDynamics.Fall.LEFT?-1:1); riding=false; }
            double speed=dynamics.speed;
            setYaw(getYaw()+(float)Math.toDegrees(dynamics.yawRate*dt));
            double dir=Math.toRadians(getYaw())-dynamics.skid;
            setStepHeight((float)((input&BicycleDynamics.BACK)!=0?.45:.19));
            boolean ledge=f.y-getY()>getStepHeight()+.07 && f.y-r.y>.32;
            double targetBase=ledge?r.y:(f.y+r.y)*.5;
            double down=vertical;
            vertical-=9.81*dt;
            double dy=vertical*dt;
            if ((f.ground.contact() || r.ground.contact()) && targetBase-getY()>=-.09) {
                dy=Math.max(dy,MathHelper.clamp(targetBase-getY(),-.07,.09)); vertical=Math.min(0,vertical)*.35;
            }
            Vec3d delta=new Vec3d(-Math.sin(dir)*speed*dt,dy,Math.cos(dir)*speed*dt);
            // Probe the leading edge ahead of the central entity collision box.
            if (riding && speed>1 && f.ground.contact() && ledge) {
                if (speed>4.3) { eject(2); riding=false; }
                dynamics.speed=0; delta=new Vec3d(0,Math.min(0,dy),0);
            }
            // The frame centre sits between the axles. At a kerb that centre can
            // already be above the road while the central entity box still clips the lip.
            if(riding && !ledge && (f.ground.contact()||r.ground.contact()) && delta.horizontalLengthSquared()>1e-8){
                Box next=getBoundingBox().offset(delta.x,Math.max(0,delta.y),delta.z);
                double needed=0;
                for(var shape:getWorld().getBlockCollisions(this,next))
                    needed=Math.max(needed,shape.getMax(Direction.Axis.Y)-getY());
                if(needed>0 && needed<=getStepHeight()+.001 &&
                    getWorld().isSpaceEmpty(this,getBoundingBox().offset(delta.x,needed+.001,delta.z)))
                    delta=new Vec3d(delta.x,Math.max(delta.y,needed+.001),delta.z);
            }
            double previousX=getX(),previousZ=getZ();
            move(MovementType.SELF,delta);
            if (horizontalCollision && Math.abs(speed)>1) {
                double dx=getX()-previousX,dz=getZ()-previousZ;
                double blocked=Math.hypot(delta.x-dx,delta.z-dz)/dt;
                if (riding && blocked>4.3) { eject(2,blocked); riding=false; }
                double tangent=Math.hypot(dx,dz)/dt;
                dynamics.speed=Math.copySign(Math.min(Math.abs(speed),tangent),speed);
                if(tangent>.5 && blocked>.1)setYaw((float)Math.toDegrees(Math.atan2(-dx,dz))+(speed<0?180:0));
            }
            if (isOnGround() || verticalCollision && dy<0) {
                if(down<-.95){impactFork=Math.min(.1,-down*.013);landingPulse=Math.min(1,-down*.16);}
                if (riding && down < -5.5 && (Math.abs(dynamics.lean)>.30 || down < -8.5)) { eject(dynamics.lean>0?1:-1); riding=false; }
                vertical=0;
            }
            if (riding && Math.abs(dynamics.lean)>.57) {
                double side=Math.signum(dynamics.lean);
                double phase=dynamics.crank+(side>0?0:Math.PI);
                double height=.31-.17*Math.cos(phase);
                if (getY()+height*Math.cos(dynamics.lean)-.17*Math.abs(Math.sin(dynamics.lean)) < probe(local(0,.17*side)).y+.012)
                    dynamics.instability+=dt*2.6;
            }
        }
        tipped += (restLean()-tipped)*(fallSide!=0?.24:.30);
        publish();
    }
    private int supportSide;
    private double restLean() {
        if(fallSide!=0) return Math.copySign(1.38,fallSide)*(fallSide==2?Math.min(1,fallTicks/17.0):1);
        if(hasPassengers()) return dynamics.lean;
        return pushing!=null?-.04:-.15;
    }
    private static float wrapped(double radians) { return (float)Math.IEEEremainder(radians,2*Math.PI); }
    private void publish() {
        double overBars=fallSide==2 && fallTicks<28 ? Math.sin(Math.PI*fallTicks/28)*Math.min(1.37,impactSpeed*.14) : 0;
        double lift=(input&BicycleDynamics.BACK)!=0 && dynamics.speed>1 ? .10 : 0;
        dataTracker.set(ANGLES,new Vector3f((float)tipped,(float)dynamics.steer,(float)(dynamics.pitch-dynamics.rearLift*.45-overBars+lift)));
        dataTracker.set(PHASE,new Vector3f((float)Math.IEEEremainder(dynamics.frontPhase,8*Math.PI),
            (float)Math.IEEEremainder(dynamics.rearPhase,8*Math.PI),(float)Math.IEEEremainder(dynamics.crank,8*Math.PI)));
        dataTracker.set(CONTROLS,new Vector3f((float)dynamics.frontBrake,(float)dynamics.rearBrake,(float)Math.min(.1,dynamics.fork+impactFork)));
        dataTracker.set(MOTION,new Vector3f((float)dynamics.speed,(float)dynamics.skid,(float)dynamics.cadence));
        dataTracker.set(RIDER,new Vector3f(wrapped(dynamics.freehub),(float)dynamics.fatigue,(float)landingPulse));
        int flags=dynamics.gear | ((input&BicycleDynamics.STAND)!=0?16:0) | (fallSide!=0?32:0) | (pushing!=null?64:0) | (supportSide<0?128:supportSide>0?256:0) | (steeringTicks>0&&steeringTicks<12?512:0) | (shiftTicks>0?1024:0) | ((input&BicycleDynamics.BACK)!=0?2048:0) | ((input&BicycleDynamics.FORWARD)!=0?8192:0);
        dataTracker.set(FLAGS,flags | (landingPulse>.17?4096:0) | session<<16);
    }
    @Override protected void writeCustomDataToNbt(NbtCompound tag) {
        tag.putInt("Gear",dynamics.gear); tag.putInt("FallSide",fallSide); tag.putDouble("Crank",wrapped(dynamics.crank)); tag.putDouble("Speed",dynamics.speed);
    }
    @Override protected void readCustomDataFromNbt(NbtCompound tag) {
        if(tag.contains("Gear")) dynamics.selected=dynamics.gear=MathHelper.clamp(tag.getInt("Gear"),0,9);
        fallSide=MathHelper.clamp(tag.getInt("FallSide"),-1,2);
        double stored=tag.getDouble("Speed"); dynamics.speed=Double.isFinite(stored)?MathHelper.clamp(stored,-7,24):0;
        double crank=tag.getDouble("Crank"); dynamics.crank=Double.isFinite(crank)?wrapped(crank):0;
        fallTicks=120;tipped=restLean(); publish();
    }
}
