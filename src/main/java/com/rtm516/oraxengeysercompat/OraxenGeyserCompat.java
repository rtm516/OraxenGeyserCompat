package com.rtm516.oraxengeysercompat;

import io.th0rgal.oraxen.api.OraxenBlocks;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.api.OraxenPack;
import io.th0rgal.oraxen.api.events.OraxenPackGeneratedEvent;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.item.custom.CustomItemData;
import org.geysermc.geyser.api.item.custom.CustomItemOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.predicate.item.ItemRangeDispatchPredicate;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.pack.converter.PackConverter;
import org.geysermc.pack.converter.converter.Converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

public final class OraxenGeyserCompat extends JavaPlugin implements EventRegistrar, Listener {
    private Path packPath;

    @Override
    public void onEnable() {
        // Plugin startup logic
        GeyserApi.api().eventBus().subscribe(this, GeyserDefineCustomItemsEvent.class, this::onGeyserDefineCustomItemsEvent);
        GeyserApi.api().eventBus().subscribe(this, GeyserDefineCustomBlocksEvent.class, this::onGeyserDefineCustomBlocksEvent);
        GeyserApi.api().eventBus().subscribe(this, SessionLoadResourcePacksEvent.class, this::onSessionLoadResourcePacksEvent);

        getServer().getPluginManager().registerEvents(this, this);

        this.getDataFolder().mkdirs();

        packPath = this.getDataPath().resolve("oraxen.mcpack");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public void onGeyserDefineCustomItemsEvent(GeyserDefineCustomItemsEvent event) {
        getLogger().info("GeyserDefineCustomItemsEvent fired!");

        OraxenItems.entryStream().forEach((entry) -> {
            ItemBuilder item = entry.getValue();
            String itemId = entry.getKey() + " (" + item.getType().key() + "/" + item.getOraxenMeta().getCustomModelData() + ")";
            getLogger().info("Registering item: " + itemId);
            try {
                CustomItemBedrockOptions.Builder itemOptions = CustomItemBedrockOptions.builder()
                    .icon(entry.getKey());

                CustomItemDefinition.Builder data;

                if (item.getItemModel() != null) {
                    data = CustomItemDefinition.builder(Identifier.of("oraxen", entry.getKey()), Identifier.of(item.getItemModel().toString()));
                } else if (item.getOraxenMeta().getCustomModelData() != null) {
                    data = CustomItemDefinition.builder(Identifier.of("oraxen", entry.getKey()), Identifier.of(item.getType().key().toString()))
                        .predicate(ItemRangeDispatchPredicate.legacyCustomModelData(item.getOraxenMeta().getCustomModelData()));
                } else {
                    getLogger().warning("TODO Item " + itemId + " has no model or type, skipping registration.");
                    return;
                }

                data = data
                    .displayName(entry.getKey())
                    .bedrockOptions(itemOptions);

                event.register(Identifier.of(item.getType().key().toString()), data.build());
            } catch (Exception e) {
                getLogger().warning("Failed to register item: " + itemId);
                e.printStackTrace();
            }
        });
    }

    public void onGeyserDefineCustomBlocksEvent(GeyserDefineCustomBlocksEvent event) {
        getLogger().info("GeyserDefineCustomBlocksEvent fired!");

        OraxenItems.entryStream().forEach((entry) -> {
            if (!OraxenBlocks.isOraxenBlock(entry.getKey())) return;
            BlockData blockData = OraxenBlocks.getOraxenBlockData(entry.getKey());

            String itemId = entry.getKey() + " (" + blockData.getAsString() + ")";
            getLogger().info("Registering block: " + itemId);

            BoxComponent box = BoxComponent.fullBox();

            CustomBlockComponents components = CustomBlockComponents.builder()
                .geometry(GeometryComponent.builder()
                    .identifier("minecraft:geometry.full_block")
                    .build())
                .materialInstance("*", MaterialInstance.builder()
                    .texture(entry.getKey())
                    .renderMethod("alpha_test")//blockData.isOccluding() ? "opaque" : "blend")
                    .faceDimming(true)
                    .ambientOcclusion(true)
                    .build())
                .collisionBox(box)
                .selectionBox(box)
                .build();

            CustomBlockData.Builder data = CustomBlockData.builder()
                .name(entry.getKey())
                .components(components);

            // minecraft:note_block[instrument=basedrum,note=2,powered=false]
            String blockDataString = blockData.getAsString();
            String[] properties = blockDataString.substring(blockDataString.indexOf("[") + 1, blockDataString.indexOf("]")).split(",");

            for (String pair : properties) {
                String[] keyValue = pair.split("=");
                String key = keyValue[0];
                String value = keyValue[1];

                // if value boolean
                if (value.equals("true") || value.equals("false")) {
                    if (value.equals("true")) data.booleanProperty(key);
                } else if (value.matches("\\d+")) {
                    // if value int
                    data.intProperty(key, Collections.singletonList(Integer.parseInt(value)));
                } else {
                    // if value string
                    data.stringProperty(key, Collections.singletonList(value));
                }
            }

            CustomBlockData finalData = data.build();
            event.register(finalData);
            event.registerItemOverride(blockData.getAsString(), finalData);
        });
    }

    @EventHandler
    public void onOraxenPackGeneratedEvent(OraxenPackGeneratedEvent event) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            try {
                getLogger().info("Converting Oraxen pack to MCPack format...");

                Path source = OraxenPack.getPack().toPath();
                new PackConverter()
                    .logListener(new PackConverterLogger(getLogger()))
                    .input(source)
                    .output(packPath)
                    .vanillaPackPath(this.getDataPath().resolve("vanilla-pack.zip"))
                    .converters(defaultConverters())
                    .postProcessor((resourcePack, bedrockResourcePack) -> {
                        OraxenItems.entryStream().forEach((entry) -> {
                            ItemBuilder item = entry.getValue();

                            String textureLocation = "";
                            if (item.getOraxenMeta().hasLayers()) {
                                textureLocation = "textures/" + item.getOraxenMeta().getLayers().getFirst();
                            } else {
                                textureLocation = "textures/" + item.getOraxenMeta().getModelName();
                            }

                            if (OraxenBlocks.isOraxenBlock(entry.getKey())) {
                                bedrockResourcePack.addBlockTexture(entry.getKey(), textureLocation);
                            } else {
                                bedrockResourcePack.addItemTexture(entry.getKey(), textureLocation);
                            }
                        });
                    })
                    .convert()
                    .pack();

                getLogger().info("Oraxen pack converted to MCPack format!");
            } catch (Exception e) {
                getLogger().warning("Failed to convert Oraxen pack to MCPack format!");
                e.printStackTrace();
            }
        });
    }

    public void onSessionLoadResourcePacksEvent(SessionLoadResourcePacksEvent event) {
        event.register(ResourcePack.create(PackCodec.path(packPath)));
    }

    public List<? extends Converter<?>> defaultConverters() {
        // We cant use the `Converters.defaultConverters()` method because it doesnt take in the plugin classloader
        return ServiceLoader.load(Converter.class, this.getClassLoader()).stream()
            .map(ServiceLoader.Provider::get)
            .map(c -> (Converter<?>)c)
            .filter(converter -> !converter.isExperimental())
            .toList();
    }
}
