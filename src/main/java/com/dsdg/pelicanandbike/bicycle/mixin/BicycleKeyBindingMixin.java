package com.dsdg.pelicanandbike.bicycle.mixin;

import com.dsdg.pelicanandbike.bicycle.client.BicycleClient;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public class BicycleKeyBindingMixin {
    @Shadow @Final private static Map<String,KeyBinding> KEYS_BY_ID;
    @Shadow @Final private String category;
    @Shadow private InputUtil.Key boundKey;
    @Shadow private int timesPressed;
    @Unique private static final Set<InputUtil.Key> bicycle$keys=new HashSet<>();

    // Vanilla stores only one binding per physical key. Route shared keys to
    // the active context so F still swaps hands when the player is on foot.
    @Inject(method="onKeyPressed",at=@At("HEAD"),cancellable=true)
    private static void bicycle$dispatchPress(InputUtil.Key key,CallbackInfo ci){
        if(!bicycle$keys.contains(key))return;
        for(var binding:KEYS_BY_ID.values()){
            var access=(BicycleKeyBindingMixin)(Object)binding;
            if(key.equals(access.boundKey)&&!access.bicycle$inactive())access.timesPressed++;
        }
        ci.cancel();
    }
    @Inject(method="setKeyPressed",at=@At("HEAD"),cancellable=true)
    private static void bicycle$dispatchState(InputUtil.Key key,boolean pressed,CallbackInfo ci){
        if(!bicycle$keys.contains(key))return;
        for(var binding:KEYS_BY_ID.values()){
            var access=(BicycleKeyBindingMixin)(Object)binding;
            if(key.equals(access.boundKey))binding.setPressed(pressed&&!access.bicycle$inactive());
        }
        ci.cancel();
    }
    @Inject(method="updateKeysByCode",at=@At("TAIL"))
    private static void bicycle$refreshKeys(CallbackInfo ci){
        bicycle$keys.clear();
        for(var binding:KEYS_BY_ID.values()){
            if(!binding.getCategory().equals("category.pelican_and_bike.bicycle")||binding.isUnbound())continue;
            bicycle$keys.add(KeyBindingHelper.getBoundKeyOf(binding));
        }
    }
    @Unique private boolean bicycle$inactive(){
        return bicycle$keys.contains(boundKey) && BicycleClient.usingBicycleKeys()!=category.equals("category.pelican_and_bike.bicycle");
    }
    @Inject(method="wasPressed",at=@At("HEAD"),cancellable=true)
    private void bicycle$consumeInactivePress(CallbackInfoReturnable<Boolean> ci){
        if(bicycle$inactive()){timesPressed=0;ci.setReturnValue(false);}
    }
    @Inject(method="isPressed",at=@At("HEAD"),cancellable=true)
    private void bicycle$filterHeld(CallbackInfoReturnable<Boolean> ci){
        if(bicycle$inactive())ci.setReturnValue(false);
    }
}
