package com.dsdg.pelicanandbike.pelican;

import com.dsdg.pelicanandbike.bicycle.*;
import static com.dsdg.pelicanandbike.bicycle.BicycleDynamics.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.*;

public class PelicanEntity extends PathAwareEntity implements GeoEntity {
    public enum Pose { IDLE,APPROACH,BOARD,RIDE,STEP_OFF,FALL_LEFT,FALL_RIGHT,OVER_BARS,TIP_LEFT,TIP_RIGHT,RECOVER,LIFT,
        WADE,SWIM,HERD,SCOOP,MISS,DRAIN,SWALLOW,PREEN,RUN,FLAP,GLIDE,LAND }
    public record Controls(int mask,int shift) {}
    private static final Pose[] POSES=Pose.values();
    private static final Map<Pose,RawAnimation> CLIPS=new EnumMap<>(Pose.class);
    static { for(var p:POSES){
        String clip="animation.pelican."+p.name().toLowerCase(Locale.ROOT);
        CLIPS.put(p,switch(p){
            case BOARD,STEP_OFF,FALL_LEFT,FALL_RIGHT,OVER_BARS,TIP_LEFT,TIP_RIGHT,RECOVER,LIFT,SCOOP,MISS,DRAIN,SWALLOW,PREEN -> RawAnimation.begin().thenPlayAndHold(clip);
            default -> RawAnimation.begin().thenLoop(clip);
        });
    }}
    private static final TrackedData<Integer> ACTION=DataTracker.registerData(PelicanEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> SINCE=DataTracker.registerData(PelicanEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> SIDE=DataTracker.registerData(PelicanEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> BIKE=DataTracker.registerData(PelicanEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private BicycleEntity chosen;
    private BicycleEntity ridingLeader;
    private FishEntity fish;
    private Vec3d standAt,shore,water,heading,flightCentre;
    private int cooldown,fullness,rideTicks,nextSearch,parkTicks,flightTicks;
    private double impactEnergy,downward;
    private boolean caught;
    public PelicanEntity(EntityType<? extends PelicanEntity> type,World w){super(type,w);setStepHeight(.65f);}
    public static DefaultAttributeContainer.Builder attributes(){return MobEntity.createMobAttributes()
        .add(EntityAttributes.GENERIC_MAX_HEALTH,18).add(EntityAttributes.GENERIC_MOVEMENT_SPEED,.23)
        .add(EntityAttributes.GENERIC_FOLLOW_RANGE,24);}
    @Override protected void initDataTracker(){super.initDataTracker();dataTracker.startTracking(ACTION,0);dataTracker.startTracking(SINCE,0L);dataTracker.startTracking(SIDE,1);dataTracker.startTracking(BIKE,-1);}
    public BicycleEntity displayBike(){return getVehicle() instanceof BicycleEntity b?b:getWorld().getEntityById(dataTracker.get(BIKE)) instanceof BicycleEntity b?b:null;}
    @Override protected void initGoals(){goalSelector.add(7,new WanderAroundFarGoal(this,.70,0.0f){
        @Override public boolean canStart(){return pose()==Pose.IDLE&&super.canStart();}
        @Override public boolean shouldContinue(){return pose()==Pose.IDLE&&super.shouldContinue();}
    });}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar r){r.add(new AnimationController<>(this,"pelican",3,s->s.setAndContinue(CLIPS.get(pose()))));}
    public Pose pose(){return POSES[MathHelper.clamp(dataTracker.get(ACTION),0,POSES.length-1)];}
    public int elapsed(){return (int)Math.max(0,getWorld().getTime()-dataTracker.get(SINCE));}
    public int side(){return dataTracker.get(SIDE);}
    private void enter(Pose p){
        if(pose()==p)return;
        dataTracker.set(ACTION,p.ordinal());dataTracker.set(SINCE,getWorld().getTime());
        if(p!=Pose.APPROACH&&p!=Pose.WADE)getNavigation().stop();
        setNoGravity(p==Pose.FLAP||p==Pose.GLIDE||p==Pose.LAND);
    }
    private static BirdBicycle bridge(BicycleEntity b){return (BirdBicycle)(Object)b;}
    private boolean validBike(){return chosen!=null&&chosen.isAlive()&&chosen.getWorld()==getWorld()&&!chosen.hasPassengers();}
    private Vec3d sidePoint(BicycleEntity b,int side){
        return b.getPos().add(new Vec3d(side*.92,0,-.04).rotateY((float)-Math.toRadians(b.getYaw())));
    }
    private boolean footing(Vec3d at){
        BlockPos below=BlockPos.ofFloored(at.add(0,-.12,0));
        return !getWorld().getBlockState(below).getCollisionShape(getWorld(),below).isEmpty()
            &&getWorld().getFluidState(below.up()).isEmpty()
            &&getWorld().isSpaceEmpty(this,getDimensions(getPose()).getBoxAt(at.add(0,.06,0)).expand(.06,.02,.06));
    }
    private boolean chooseStand(BicycleEntity b){
        int near=squaredDistanceTo(sidePoint(b,-1))<squaredDistanceTo(sidePoint(b,1))?-1:1;
        for(int s:new int[]{near,-near}){
            Vec3d pos=sidePoint(b,s);
            if(!footing(pos)||!getWorld().isSpaceEmpty(this,new Box(b.getPos().add(-.57,1.05,-.56),b.getPos().add(.57,2.02,.56))))continue;
            var route=getNavigation().findPathTo(BlockPos.ofFloored(pos),0);
            if(squaredDistanceTo(pos)>.25&&(route==null||!route.reachesTarget()))continue;
            standAt=pos;dataTracker.set(SIDE,s);
            if(route!=null)getNavigation().startMovingAlong(route,1.05);
            return true;
        }return false;
    }
    private void seekBike(){
        var bikes=getWorld().getEntitiesByClass(BicycleEntity.class,getBoundingBox().expand(8,3,8),
            b->!b.hasPassengers()&&(b.flags()&(32|64))==0&&Math.abs(b.dynamics.speed)<.18&&squaredDistanceTo(b)<=64);
        bikes.sort(Comparator.comparingDouble(this::squaredDistanceTo));
        for(var b:bikes){
            if(!bridge(b).pelican$reserve(this))continue;
            if(chooseStand(b)){chosen=b;dataTracker.set(BIKE,b.getId());enter(Pose.APPROACH);return;}
            bridge(b).pelican$release(this);
        }
    }
    private void abandon(){if(chosen!=null)bridge(chosen).pelican$release(this);chosen=null;standAt=null;dataTracker.set(BIKE,-1);cooldown=45;enter(Pose.IDLE);}
    private void approach(){
        if(!validBike()||!bridge(chosen).pelican$reserve(this)||!footing(standAt)||elapsed()>125){abandon();return;}
        getLookControl().lookAt(chosen,32,28);
        if(pose()==Pose.APPROACH){
            if(squaredDistanceTo(standAt)<.18){enter(Pose.BOARD);setVelocity(0,getVelocity().y,0);face(chosen.getPos(),50);}
            else if(squaredDistanceTo(standAt)<2.25 && Math.abs(standAt.y-getY())<.55){
                // Land navigation ends at the block centre. Walk the remaining fraction to the pedal.
                var delta=standAt.subtract(getPos()).multiply(1,0,1);
                var step=delta.multiply(Math.min(1,.115/Math.max(.001,delta.length())));
                if(getWorld().isSpaceEmpty(this,getBoundingBox().offset(step))){
                    getNavigation().stop();face(standAt,24);setVelocity(step.x,getVelocity().y,step.z);
                }else if(age%8==0)getNavigation().startMovingTo(standAt.x,standAt.y,standAt.z,1);
            }
            else if(age%10==0)getNavigation().startMovingTo(standAt.x,standAt.y,standAt.z,1.0);
        }else if(elapsed()>=10){
            if(!startRiding(chosen)){abandon();return;}
            rideTicks=parkTicks=0;heading=null;shore=null;water=null;ridingLeader=null;
            enter(Pose.RIDE);
        }
    }
    private void face(Vec3d target,float rate){
        Vec3d diff=target.subtract(getPos());
        float want=(float)Math.toDegrees(Math.atan2(-diff.x,diff.z));
        setYaw(getYaw()+MathHelper.clamp(MathHelper.wrapDegrees(want-getYaw()),-rate,rate));
        setBodyYaw(getYaw());setHeadYaw(getYaw());
    }
    private boolean waterAt(BlockPos p){return getWorld().getFluidState(p).isIn(FluidTags.WATER);}
    private boolean findBank(Vec3d origin,int radius){
        int best=Integer.MAX_VALUE;BlockPos base=BlockPos.ofFloored(origin);
        for(int x=-radius;x<=radius;x+=2)for(int z=-radius;z<=radius;z+=2){
            int distance=x*x+z*z;if(distance>best||distance>radius*radius)continue;
            for(int dy=-2;dy<=1;dy++){
                BlockPos wet=base.add(x,dy,z);
                if(!waterAt(wet)||waterAt(wet.up())||!getWorld().isChunkLoaded(wet))continue;
                for(Direction d:Direction.Type.HORIZONTAL){
                    for(int k=1;k<=2;k++){
                        BlockPos ground=wet.offset(d,k);
                        if(waterAt(ground)||!getWorld().getBlockState(ground).isSolidBlock(getWorld(),ground))continue;
                        Vec3d at=Vec3d.ofBottomCenter(ground.up());if(!footing(at))continue;
                        Vec3d park=at.add(d.getOffsetX()*1.25,0,d.getOffsetZ()*1.25);
                        if(!footing(park))continue;
                        best=distance;shore=park;water=Vec3d.ofCenter(wet);break;
                    }
                }
            }
        }return best!=Integer.MAX_VALUE;
    }
    private Vec3d forward(BicycleEntity b){return Vec3d.fromPolar(0,b.getYaw());}
    private boolean obstacle(BicycleEntity b,Vec3d dir,double range){
        Vec3d from=b.getPos().add(0,.57,0),to=from.add(dir.multiply(range));
        return getWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,b)).getType()!=HitResult.Type.MISS;
    }
    private BicycleEntity leader(BicycleEntity b){
        return getWorld().getEntitiesByClass(BicycleEntity.class,b.getBoundingBox().expand(12,3,12),other->
            other!=b && (other.getFirstPassenger() instanceof PlayerEntity||other.getId()<b.getId()&&other.getFirstPassenger() instanceof PelicanEntity)
            &&Math.abs(other.dynamics.speed)<6&&other.getPos().subtract(b.getPos()).dotProduct(forward(b))>0)
            .stream().min(Comparator.comparingDouble(b::squaredDistanceTo)).orElse(null);
    }
    public Controls controls(BicycleEntity b){
        if(getVehicle()!=b)return new Controls(0,0);
        chosen=b;rideTicks++;if(pose()!=Pose.RIDE)enter(Pose.RIDE);
        Vec3d direction=forward(b);double speed=Math.abs(b.dynamics.speed);
        if(rideTicks%50==20 && shore==null && fullness==0)findBank(b.getPos(),16);
        if(rideTicks%9==1||heading==null){
            var lead=ridingLeader=leader(b);
            if(shore!=null)heading=shore;
            else if(lead!=null)heading=lead.getPos().subtract(forward(lead).multiply(1.4)).add(forward(lead).rotateY((float)Math.PI/2).multiply(.55));
            else if(heading==null||b.getPos().squaredDistanceTo(heading)<8)heading=b.getPos().add(direction.rotateY((float)Math.sin(rideTicks*.013)*.32f).multiply(13));
            if(obstacle(b,direction,Math.max(1.4,speed*.70))){
                Vec3d left=direction.rotateY(.85f),right=direction.rotateY(-.85f);
                heading=b.getPos().add((obstacle(b,left,3)?right:left).multiply(3));
            }
        }
        Vec3d diff=heading.subtract(b.getPos());
        double turn=MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(-diff.x,diff.z))-b.getYaw());
        int mask=Math.abs(turn)>3?(turn>0?RIGHT:LEFT):0;
        double limit=Math.abs(turn)>30?2.25:4.45;
        boolean following=shore==null&&ridingLeader!=null&&ridingLeader.isAlive()&&ridingLeader.hasPassengers();
        boolean close=false;
        if(following){
            double gap=b.distanceTo(ridingLeader),safe=2.4+speed*.38;
            limit=Math.min(limit,Math.max(0,Math.abs(ridingLeader.dynamics.speed)+(gap-safe)*.65));
            close=gap<safe-.6 || Math.abs(ridingLeader.dynamics.speed)<.18&&gap<safe+1.5;
        }
        boolean stopping=shore!=null&&b.getPos().squaredDistanceTo(shore)<Math.max(3,1+speed*speed*.19)
            ||rideTicks>420||!following&&rideTicks>125&&diff.horizontalLengthSquared()<1.8;
        if(stopping||close||obstacle(b,direction,Math.max(.9,speed*.46)))mask|=FRONT|REAR;
        else if(speed<limit-.16 && rideTicks%150<121)mask|=PEDAL;
        else if(speed>limit+.7)mask|=REAR;
        if(b.dynamics.pitch>.055 && b.dynamics.fatigue<.72 && (mask&PEDAL)!=0)mask|=STAND|FORWARD;
        if(b.dynamics.pitch<-.09||b.dynamics.frontBrake>.4)mask|=BACK;
        if(Math.abs(b.dynamics.skid)>.16)mask= b.dynamics.skid>0?LEFT:RIGHT;
        if(stopping&&speed<.28){if(++parkTicks>=5)dismountAtBank(b);}else parkTicks=0;
        int gear=b.dynamics.gear;
        int shift=(rideTicks%16==0 && (mask&PEDAL)!=0)?(b.dynamics.pitch>.06&&gear>1?-1:speed>3.1&&gear<6?1:speed<1.6&&gear>3?-1:0):0;
        return new Controls(mask,shift);
    }
    private void dismountAtBank(BicycleEntity b){
        if(!chooseStand(b)){parkTicks=0;return;}
        stopRiding();refreshPositionAndAngles(standAt.x,standAt.y,standAt.z,b.getYaw(),0);
        enter(Pose.STEP_OFF);cooldown=240;chosen=null;
    }
    public void thrown(BicycleEntity b,int direction,double impact){
        chosen=b;water=shore=null;Vec3d f=forward(b),l=f.rotateY((float)-Math.PI/2);
        double speed=b.dynamics.speed;
        setVelocity(f.multiply(speed*.05).add(l.multiply(direction==2?0:direction*Math.min(.20,.045+Math.abs(speed)*.015))).add(0,direction==2?.25:.10,0));
        setYaw(b.getYaw());setBodyYaw(getYaw());velocityDirty=true;
        impactEnergy=Math.pow(direction==2?impact:impact*.38,2);downward=0;cooldown=75;
        enter(direction==2?Pose.OVER_BARS:Math.abs(speed)<1.3?(direction<0?Pose.TIP_LEFT:Pose.TIP_RIGHT):(direction<0?Pose.FALL_LEFT:Pose.FALL_RIGHT));
    }
    private boolean falling(){return switch(pose()){case OVER_BARS,FALL_LEFT,FALL_RIGHT,TIP_LEFT,TIP_RIGHT->true;default->false;};}
    @Override public boolean handleFallDamage(float distance,float multiplier,DamageSource source){return falling()?false:super.handleFallDamage(distance,multiplier,source);}
    private void landImpact(){
        downward=Math.max(downward,-getVelocity().y*20);
        if(elapsed()<3||(!isOnGround()&&!isTouchingWater()))return;
        double damage=Math.min(18,Math.max(0,impactEnergy+downward*downward-12)/12);
        enter(Pose.RECOVER);if(!isTouchingWater()&&damage>.05)damage(getDamageSources().flyIntoWall(),(float)damage);
    }
    private void swimTo(Vec3d dest,double speed){
        Vec3d diff=dest.subtract(getPos());face(dest,12);
        double len=Math.max(.01,diff.horizontalLength());Vec3d v=getVelocity();
        setVelocity(v.x*.55+diff.x/len*speed,v.y*.50+MathHelper.clamp(diff.y*.12,-.05,.09),v.z*.55+diff.z/len*speed);
    }
    private List<PelicanEntity> neighbours(double radius){
        var birds=getWorld().getEntitiesByClass(PelicanEntity.class,getBoundingBox().expand(radius),p->p.isAlive()&&!p.hasVehicle());
        birds.sort(Comparator.comparingInt(Entity::getId));return birds;
    }
    private void fishTick(){
        if(!isTouchingWater()){enter(Pose.PREEN);return;}
        BlockPos surface=getBlockPos();while(waterAt(surface)&&surface.getY()<getWorld().getTopY())surface=surface.up();
        double level=surface.getY()-.38;
        if(pose()==Pose.SWIM&&fullness==0&&age%12==0){
            fish=getWorld().getEntitiesByClass(FishEntity.class,getBoundingBox().expand(7,2,7),f->f.isAlive()&&f.isTouchingWater())
                .stream().min(Comparator.comparingDouble(this::squaredDistanceTo)).orElse(null);
            if(fish!=null){findBank(getPos(),7);enter(Pose.HERD);}
        }
        if(pose()==Pose.HERD){
            if(fish==null||!fish.isAlive()||squaredDistanceTo(fish)>100||elapsed()>155){fish=null;enter(Pose.MISS);return;}
            Vec3d toward=shore==null?Vec3d.fromPolar(0,getYaw()):shore.subtract(fish.getPos()).multiply(1,0,1).normalize();
            var group=neighbours(8).stream().filter(p->p.fish==fish&&p.pose()==Pose.HERD).limit(3).toList();
            int index=group.indexOf(this);double slot=index<0?0:index-(group.size()-1)*.5;
            Vec3d aim=fish.getPos().subtract(toward.multiply(.74)).add(toward.rotateY((float)Math.PI/2).multiply(slot*.88));
            swimTo(new Vec3d(aim.x,level,aim.z),.060);
            if(group.size()>1&&squaredDistanceTo(fish)<9&&waterAt(fish.getBlockPos().add((int)Math.signum(toward.x),0,(int)Math.signum(toward.z))))
                fish.addVelocity(toward.x*.019,0,toward.z*.019);
            if(squaredDistanceTo(fish)<1.85 && elapsed()>8+(index<0?0:index)*4){face(fish.getPos(),180);enter(Pose.SCOOP);}
        }else if(pose()==Pose.SCOOP && elapsed()>=8){
            Vec3d bill=getPos().add(Vec3d.fromPolar(0,getYaw()).multiply(.66));
            caught=fish!=null&&fish.isAlive()&&fish.getPos().squaredDistanceTo(bill)<1.55;
            if(caught){fish.discard();enter(Pose.DRAIN);}else enter(Pose.MISS);
            fish=null;
        }else if(pose()==Pose.DRAIN&&elapsed()>=14)enter(Pose.SWALLOW);
        else if(pose()==Pose.SWALLOW&&elapsed()>=13){if(caught){fullness=500;heal(2);}caught=false;enter(Pose.SWIM);}
        else if(pose()==Pose.MISS&&elapsed()>=16)enter(Pose.SWIM);
        if(pose()!=Pose.HERD)swimTo(getPos().add(Vec3d.fromPolar(0,getYaw()).multiply(.55)).add(0,level-getY(),0),pose()==Pose.SWIM?.024:.005);
        if(pose()==Pose.SWIM&&elapsed()>22&&(age+getId())%10==0){
            var takingOff=neighbours(13).stream().filter(p->p!=this&&(p.pose()==Pose.RUN||p.pose()==Pose.FLAP)).findFirst();
            if(takingOff.isPresent())face(getPos().add(Vec3d.fromPolar(0,takingOff.get().getYaw())),28);
            if((takingOff.isPresent()||elapsed()>55&&fullness>0||elapsed()>260)&&!obstacleAhead(3)){
                enter(Pose.RUN);flightTicks=0;flightCentre=getPos();
            }
        }
    }
    private boolean obstacleAhead(double range){
        Vec3d a=getPos().add(0,.7,0);
        return getWorld().raycast(new RaycastContext(a,a.add(Vec3d.fromPolar(0,getYaw()).multiply(range)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,this)).getType()!=HitResult.Type.MISS;
    }
    private void fly(){
        if(pose()==Pose.RUN){
            if(obstacleAhead(2)||!getWorld().isSpaceEmpty(this,getBoundingBox().offset(0,1.05,0))){enter(isTouchingWater()?Pose.SWIM:Pose.PREEN);return;}
            Vec3d d=Vec3d.fromPolar(0,getYaw());setVelocity(d.multiply(.14).add(0,.065,0));
            if(elapsed()>=17){enter(Pose.FLAP);setVelocity(d.multiply(.18).add(0,.19,0));}
            return;
        }
        flightTicks++;
        if(flightCentre==null)flightCentre=getPos();
        Vec3d dest=flightCentre.add(8*Math.sin(flightTicks*.029),4.5,8*Math.cos(flightTicks*.029));
        if(flightTicks%8==1){
            var lead=neighbours(14).stream().filter(p->p.getId()<getId()&&(p.pose()==Pose.FLAP||p.pose()==Pose.GLIDE)).findFirst();
            if(lead.isPresent())heading=lead.get().getPos().subtract(Vec3d.fromPolar(0,lead.get().getYaw()).multiply(2.4)).add(1.1,.3,0);else heading=null;
        }
        if(heading!=null)dest=heading;
        if(flightTicks>125||obstacleAhead(1.8))enter(Pose.LAND);
        if(pose()==Pose.LAND){
            Vec3d f=Vec3d.fromPolar(0,getYaw());setVelocity(f.multiply(obstacleAhead(.9)?0:.09).add(0,-.10,0));
            if(isOnGround()||isTouchingWater()){enter(isTouchingWater()?Pose.SWIM:Pose.PREEN);cooldown=60;}return;
        }
        enter(flightTicks%56<27?Pose.FLAP:Pose.GLIDE);face(dest,6);
        setVelocity(Vec3d.fromPolar(0,getYaw()).multiply(.17).add(0,MathHelper.clamp((dest.y-getY())*.08,-.1,.15),0));
    }
    @Override public void tick(){
        if(!getWorld().isClient&&falling())downward=Math.max(downward,-getVelocity().y*20);
        super.tick();
        if(getWorld().isClient||!isAlive())return;
        if(cooldown>0)cooldown--;if(fullness>0)fullness--;
        if(getVehicle() instanceof BicycleEntity b){setBodyYaw(b.getYaw());setYaw(b.getYaw());return;}
        if(falling()){landImpact();return;}
        switch(pose()){
            case RIDE -> {cooldown=90;enter(Pose.STEP_OFF);}
            case APPROACH,BOARD -> approach();
            case STEP_OFF -> {if(elapsed()>13){if(water!=null){enter(Pose.WADE);getNavigation().startMovingTo(water.x,water.y,water.z,1.05);}else enter(Pose.IDLE);}}
            case RECOVER -> {if(elapsed()>23){if(validBike()&&(chosen.flags()&32)!=0&&squaredDistanceTo(chosen)<144&&chooseStand(chosen))enter(Pose.LIFT);else abandon();}}
            case LIFT -> {
                if(!validBike()||elapsed()>85){abandon();break;}
                if(squaredDistanceTo(standAt)>.42)getNavigation().startMovingTo(standAt.x,standAt.y,standAt.z,1);
                else {face(chosen.getPos(),30);if(elapsed()>=19 && bridge(chosen).pelican$right(this)){cooldown=15;chosen=null;enter(Pose.IDLE);}}
            }
            case WADE -> {if(isTouchingWater())enter(Pose.SWIM);else if(elapsed()>65)enter(Pose.IDLE);}
            case SWIM,HERD,SCOOP,DRAIN,MISS,SWALLOW -> fishTick();
            case RUN,FLAP,GLIDE,LAND -> fly();
            case PREEN -> {if(elapsed()>43)enter(isTouchingWater()?Pose.SWIM:Pose.IDLE);}
            case IDLE -> {
                if(isTouchingWater()){enter(Pose.SWIM);break;}
                if(age>=nextSearch&&cooldown==0&&hurtTime==0){nextSearch=age+12;seekBike();}
                if(pose()==Pose.IDLE&&age%160==0&&fullness==0&&findBank(getPos(),8)){
                    enter(Pose.WADE);getNavigation().startMovingTo(water.x,water.y,water.z,1);
                }
            }
        }
    }
    @Override public void writeCustomDataToNbt(NbtCompound n){super.writeCustomDataToNbt(n);n.putInt("Satiety",fullness);n.putInt("RideRest",cooldown);
        n.putString("Activity",pose().name());n.putDouble("ImpactEnergy",impactEnergy);n.putDouble("Descent",downward);n.putBoolean("FishInPouch",caught);}
    @Override public void readCustomDataFromNbt(NbtCompound n){super.readCustomDataFromNbt(n);fullness=MathHelper.clamp(n.getInt("Satiety"),0,500);cooldown=MathHelper.clamp(n.getInt("RideRest"),0,240);
        try{var p=Pose.valueOf(n.getString("Activity"));switch(p){case RIDE,RECOVER,FALL_LEFT,FALL_RIGHT,TIP_LEFT,TIP_RIGHT,OVER_BARS,DRAIN,SWALLOW->enter(p);default->enter(Pose.IDLE);}}
        catch(IllegalArgumentException e){enter(Pose.IDLE);}
        impactEnergy=n.getDouble("ImpactEnergy");downward=n.getDouble("Descent");caught=n.getBoolean("FishInPouch");}
}
