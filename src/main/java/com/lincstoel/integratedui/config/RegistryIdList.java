package com.lincstoel.integratedui.config;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A config list of registry ids, and tags given with a leading {@code #}, such as the container item list or the mount
 * list. Parsed again whenever the config's list changes.
 */
public final class RegistryIdList<T>
{
    private final Registry<T> registry;
    private final ModConfigSpec.ConfigValue<List<? extends String>> value;
    @Nullable
    private volatile Parsed<T> parsed;

    public RegistryIdList(Registry<T> registry, ModConfigSpec.ConfigValue<List<? extends String>> value)
    {
        this.registry = registry;
        this.value = value;
    }

    /** Whether the entry is on the list, by id or by one of the listed tags. */
    public boolean matches(T entry)
    {
        Parsed<T> current = this.get();
        Holder<T> holder = this.registry.wrapAsHolder(entry);
        if(holder.unwrapKey().map(key -> current.ids.contains(key.location())).orElse(false))
        {
            return true;
        }
        for(TagKey<T> tag : current.tags)
        {
            if(holder.is(tag))
            {
                return true;
            }
        }
        return false;
    }

    private Parsed<T> get()
    {
        List<? extends String> entries = ServerConfig.get(this.value);
        Parsed<T> current = this.parsed;
        if(current == null || current.entries != entries)
        {
            current = Parsed.parse(this.registry, entries);
            this.parsed = current;
        }
        return current;
    }

    private record Parsed<T>(List<? extends String> entries, Set<ResourceLocation> ids, List<TagKey<T>> tags)
    {
        static <T> Parsed<T> parse(Registry<T> registry, List<? extends String> entries)
        {
            Set<ResourceLocation> ids = new HashSet<>();
            List<TagKey<T>> tags = new ArrayList<>();
            for(String entry : entries)
            {
                boolean tag = entry.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? entry.substring(1) : entry);
                if(id == null)
                {
                    continue;
                }
                if(tag)
                {
                    tags.add(TagKey.create(registry.key(), id));
                }
                else
                {
                    ids.add(id);
                }
            }
            return new Parsed<>(entries, ids, tags);
        }
    }
}
