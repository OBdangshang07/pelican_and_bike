package com.dsdg.pelicanandbike.pelican;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;

public final class PelicanModule implements ModInitializer {
    public static Identifier id(String path){return new Identifier("pelican_and_bike",path);}
    public static final EntityType<PelicanEntity> TYPE=Registry.register(Registries.ENTITY_TYPE,id("pelican"),
        FabricEntityTypeBuilder.create(SpawnGroup.CREATURE,PelicanEntity::new)
            .dimensions(EntityDimensions.fixed(.82f,1.06f)).trackRangeBlocks(80).trackedUpdateRate(1).build());
    public static final Item EGG=Registry.register(Registries.ITEM,id("pelican_spawn_egg"),new SpawnEggItem(TYPE,0xeee9dc,0xe5a154,new Item.Settings()));
    @Override public void onInitialize(){
        FabricDefaultAttributeRegistry.register(TYPE,PelicanEntity.attributes());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e->e.add(EGG));
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.RIVER,BiomeKeys.SWAMP),SpawnGroup.CREATURE,TYPE,4,2,3);
        SpawnRestriction.register(TYPE,SpawnRestriction.Location.ON_GROUND,Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            (type,w,reason,pos,random)->{
                if(!w.isSkyVisible(pos)||w.getBaseLightLevel(pos,0)<8||!w.getBlockState(pos.down()).isSolidBlock(w,pos.down()))return false;
                for(BlockPos near:BlockPos.iterate(pos.add(-4,-2,-4),pos.add(4,0,4)))
                    if(w.getFluidState(near).isIn(FluidTags.WATER))return true;
                return false;
            });
    }
}
