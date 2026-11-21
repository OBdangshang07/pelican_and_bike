package com.dsdg.pelicanandbike.pelican.mixin;

import com.dsdg.pelicanandbike.bicycle.BicycleEntity;
import com.dsdg.pelicanandbike.pelican.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.UUID;

@Mixin(BicycleEntity.class)
public abstract class PelicanBicycleMixin implements BirdBicycle {
    @Shadow(remap=false) private int input,lastInput,shiftTicks,fallSide,riseTicks;
    @Shadow(remap=false) private PlayerEntity pushing;
    @Shadow(remap=false) private boolean separating;
    @Shadow(remap=false) private void markFall(int side){throw new AssertionError();}
    @Unique private UUID pelican$claim;
    @Unique private long pelican$until;
    @Unique private BicycleEntity pelican$bike(){return (BicycleEntity)(Object)this;}

    public boolean pelican$reserve(PelicanEntity bird){
        var bike=pelican$bike(); long now=bike.getWorld().getTime();
        if(bike.hasPassengers()||pushing!=null||riseTicks>0||fallSide!=0||Math.abs(bike.dynamics.speed)>.18)return false;
        if(pelican$claim!=null&&pelican$until>now&&!pelican$claim.equals(bird.getUuid()))return false;
        pelican$claim=bird.getUuid();pelican$until=now+25;return true;
    }
    public void pelican$release(PelicanEntity bird){if(bird.getUuid().equals(pelican$claim))pelican$claim=null;}
    public boolean pelican$right(PelicanEntity bird){
        var bike=pelican$bike();
        if(fallSide==0||bike.hasPassengers()||pushing!=null||Math.abs(bike.dynamics.speed)>.6||bike.squaredDistanceTo(bird)>2.7)return false;
        fallSide=0;riseTicks=12;bike.dynamics.lean=0;bike.dynamics.instability=0;return true;
    }
    @Inject(method="canAddPassenger",at=@At("HEAD"),cancellable=true)
    private void pelican$mount(Entity passenger,CallbackInfoReturnable<Boolean> ci){
        if(passenger instanceof PelicanEntity bird)ci.setReturnValue(pelican$reserve(bird));
    }
    @Inject(method="tick",at=@At("HEAD"))
    private void pelican$input(CallbackInfo ci){
        var bike=pelican$bike();
        if(bike.getWorld().isClient||!(bike.getFirstPassenger() instanceof PelicanEntity bird))return;
        var controls=bird.controls(bike);input=controls.mask();lastInput=bike.age;
        if(controls.shift()!=0&&shiftTicks==0){bike.dynamics.shift(controls.shift());shiftTicks=4;}
    }
    @Inject(method="eject(ID)V",remap=false,at=@At("HEAD"),cancellable=true)
    private void pelican$eject(int side,double impact,CallbackInfo ci){
        var bike=pelican$bike();
        if(!(bike.getFirstPassenger() instanceof PelicanEntity bird))return;
        if(fallSide==0){
            markFall(side);separating=true;
            try{bird.stopRiding();}finally{separating=false;}
            bird.thrown(bike,side,impact);
        }
        ci.cancel();
    }
    @Inject(method="removePassenger",at=@At("TAIL"))
    private void pelican$exit(Entity passenger,CallbackInfo ci){
        var bike=pelican$bike();
        if(passenger instanceof PelicanEntity bird&&!bike.getWorld().isClient){
            pelican$release(bird);
            if(!separating && bird.isAlive() && !bird.isRemoved() && Math.abs(bike.dynamics.speed)>3){
                int side=bike.dynamics.lean>0?1:-1;markFall(side);bird.thrown(bike,side,Math.abs(bike.dynamics.speed));
            }
        }
    }
}
