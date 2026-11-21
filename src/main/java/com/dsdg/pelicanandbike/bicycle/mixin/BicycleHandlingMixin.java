package com.dsdg.pelicanandbike.bicycle.mixin;

import com.dsdg.pelicanandbike.bicycle.BicycleModule;
import com.dsdg.pelicanandbike.bicycle.client.BicycleClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public class BicycleHandlingMixin {
    @Unique private ModelPart[] bicycle$parts;
    @Unique private ModelTransform[] bicycle$before;
    @Inject(method="setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",at=@At("HEAD"))
    private void bicycle$restore(LivingEntity entity,float limb,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        if(bicycle$before==null)return;
        for(int i=0;i<bicycle$parts.length;i++)bicycle$parts[i].setTransform(bicycle$before[i]);
        bicycle$before=null;
    }
    @Inject(method="setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void bicycle$onFoot(LivingEntity entity,float limb,float amount,float age,float headYaw,float pitch,CallbackInfo ci) {
        if(!(entity instanceof PlayerEntity p)||p.hasVehicle())return;
        var h=BicycleClient.handling(p.getUuid());var fall=BicycleClient.fall(p.getUuid());if(h==null&&fall==null)return;
        var model=(PlayerEntityModel<?>)(Object)this;long now=p.getWorld().getTime();
        if(bicycle$parts==null)bicycle$parts=new ModelPart[]{model.body,model.leftArm,model.rightArm,model.leftLeg,model.rightLeg,
            model.jacket,model.leftSleeve,model.rightSleeve,model.leftPants,model.rightPants};
        bicycle$before=new ModelTransform[bicycle$parts.length];
        for(int i=0;i<bicycle$parts.length;i++)bicycle$before[i]=bicycle$parts[i].getTransform();
        if(fall!=null){
            float blend=fall.landed()<0?1:MathHelper.clamp(1-(now-fall.landed())/20f,0,1);
            model.leftArm.pitch=-1.44f*blend;model.rightArm.pitch=-1.27f*blend;
            model.leftArm.roll=-.37f*blend;model.rightArm.roll=.52f*blend;
            model.leftLeg.pitch=-.65f*blend;model.rightLeg.pitch=.44f*blend;
        } else if(h.mode()==BicycleModule.PUSH) {
            model.leftArm.pitch=-.89f;model.leftArm.roll=-.30f;model.body.pitch=.09f;
        } else {
            float t=MathHelper.clamp((now-h.start())/18f,0,1);
            float reach=(float)Math.sin(Math.PI*t);
            model.body.pitch=reach*(h.mode()==BicycleModule.LIFT?.85f:.24f);
            model.leftArm.pitch=-reach*1.14f;model.rightArm.pitch=-reach*.91f;
            if(h.mode()==BicycleModule.STEP_OFF)model.rightLeg.roll=reach*.58f;
        }
        model.jacket.copyTransform(model.body);model.leftSleeve.copyTransform(model.leftArm);model.rightSleeve.copyTransform(model.rightArm);
        model.leftPants.copyTransform(model.leftLeg);model.rightPants.copyTransform(model.rightLeg);
    }
}
