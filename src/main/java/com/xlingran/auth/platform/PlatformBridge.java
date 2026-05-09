package com.xlingran.auth.platform;

import com.xlingran.auth.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * 多版本适配桥：启用时<strong>探测并缓存</strong> Bukkit / Adventure 调用点，运行时只走缓存分支。
 */
public final class PlatformBridge {

    private enum InventoryStrategy {
        LEGACY_STRING_TITLE,
        ADVENTURE_REFLECT_TITLE
    }

    private enum ItemMetaStrategy {
        LEGACY_STRING,
        ADVENTURE_REFLECT
    }

    private final JavaPlugin plugin;
    private final InventoryStrategy inventoryStrategy;
    private final ItemMetaStrategy itemMetaStrategy;

    private final Method bukkitCreateInventoryString;
    private final Method bukkitCreateInventoryComponent;

    private final Object adventureSerializer;
    private final Method adventureDeserialize;

    private final Method itemMetaDisplayName;
    private final Method itemMetaLoreList;

    public PlatformBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        Method invString = resolveCreateInventoryString();
        AdventureRuntime adventure = AdventureRuntime.tryLoad();

        if (invString != null) {
            this.inventoryStrategy = InventoryStrategy.LEGACY_STRING_TITLE;
            this.bukkitCreateInventoryString = invString;
            this.bukkitCreateInventoryComponent = null;
        } else {
            if (adventure == null) {
                throw new IllegalStateException("XlingranAuth: 服务端缺少 String 标题的 createInventory，且未找到 Adventure，无法启动。");
            }
            try {
                this.bukkitCreateInventoryComponent = resolveCreateInventoryComponent(adventure.componentClass);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("XlingranAuth: 无法绑定 Component 版 createInventory。", e);
            }
            this.inventoryStrategy = InventoryStrategy.ADVENTURE_REFLECT_TITLE;
            this.bukkitCreateInventoryString = null;
        }

        this.adventureSerializer = adventure == null ? null : adventure.serializer;
        this.adventureDeserialize = adventure == null ? null : adventure.deserialize;

        Method dn = null;
        Method lr = null;
        ItemMetaStrategy ms = ItemMetaStrategy.LEGACY_STRING;
        if (adventure != null) {
            try {
                ItemMeta probe = Bukkit.getItemFactory().getItemMeta(Material.STONE);
                Class<?> metaClass = probe.getClass();
                Method dnT = metaClass.getMethod("displayName", adventure.componentClass);
                Method lrT = metaClass.getMethod("lore", List.class);
                dn = dnT;
                lr = lrT;
                ms = ItemMetaStrategy.ADVENTURE_REFLECT;
            } catch (ReflectiveOperationException ignored) {
                dn = null;
                lr = null;
                ms = ItemMetaStrategy.LEGACY_STRING;
            }
        }
        this.itemMetaDisplayName = dn;
        this.itemMetaLoreList = lr;
        this.itemMetaStrategy = ms;

        plugin.getLogger().info("[XlingranAuth] 平台桥: 界面标题=" + inventoryStrategy + ", 物品元数据=" + itemMetaStrategy);
    }

    private static Method resolveCreateInventoryString() {
        try {
            return Bukkit.class.getMethod("createInventory", InventoryHolder.class, int.class, String.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method resolveCreateInventoryComponent(Class<?> componentClass) throws ReflectiveOperationException {
        for (Method m : Bukkit.class.getMethods()) {
            if (!"createInventory".equals(m.getName()) || m.getParameterCount() != 3) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (!InventoryHolder.class.isAssignableFrom(p[0]) || p[1] != int.class) {
                continue;
            }
            if (p[2] == String.class || p[2].isPrimitive()) {
                continue;
            }
            if (p[2].isAssignableFrom(componentClass) || componentClass.isAssignableFrom(p[2])) {
                return m;
            }
        }
        throw new NoSuchMethodException("createInventory(Holder,int,ComponentLike)");
    }

    public Inventory createInventory(InventoryHolder holder, int size, String legacySectionTitle) {
        try {
            if (inventoryStrategy == InventoryStrategy.LEGACY_STRING_TITLE) {
                return (Inventory) bukkitCreateInventoryString.invoke(null, holder, size, legacySectionTitle);
            }
            Object comp = adventureDeserialize.invoke(adventureSerializer, legacySectionTitle);
            return (Inventory) bukkitCreateInventoryComponent.invoke(null, holder, size, comp);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.SEVERE, "createInventory 失败", e);
            throw new IllegalStateException(e);
        }
    }

    public void applyItemMetaText(ItemMeta meta, String displayNameRaw, List<String> loreRaw) {
        String display = Texts.colorize(displayNameRaw);
        List<String> lore = new ArrayList<>();
        for (String line : loreRaw) {
            lore.add(Texts.colorize(line));
        }
        if (itemMetaStrategy != ItemMetaStrategy.ADVENTURE_REFLECT
                || adventureSerializer == null
                || itemMetaDisplayName == null
                || itemMetaLoreList == null) {
            meta.setDisplayName(display);
            meta.setLore(lore);
            return;
        }
        try {
            Object titleComp = adventureDeserialize.invoke(adventureSerializer, display);
            itemMetaDisplayName.invoke(meta, titleComp);
            List<Object> loreComps = new ArrayList<>();
            for (String line : lore) {
                loreComps.add(adventureDeserialize.invoke(adventureSerializer, line));
            }
            itemMetaLoreList.invoke(meta, loreComps);
        } catch (Throwable t) {
            meta.setDisplayName(display);
            meta.setLore(lore);
        }
    }

    private static final class AdventureRuntime {
        private final Class<?> componentClass;
        private final Object serializer;
        private final Method deserialize;

        private AdventureRuntime(Class<?> componentClass, Object serializer, Method deserialize) {
            this.componentClass = componentClass;
            this.serializer = serializer;
            this.deserialize = deserialize;
        }

        static AdventureRuntime tryLoad() {
            try {
                Class<?> lcs = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
                Object serializer = null;
                Method deserialize = null;
                String[] names = {"legacySection", "legacyAmpersand"};
                for (String n : names) {
                    try {
                        Method factory = lcs.getMethod(n);
                        serializer = factory.invoke(null);
                        break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                if (serializer == null) {
                    for (Method m : lcs.getMethods()) {
                        if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                                && m.getParameterCount() == 0
                                && lcs.isAssignableFrom(m.getReturnType())) {
                            serializer = m.invoke(null);
                            break;
                        }
                    }
                }
                if (serializer == null) {
                    return null;
                }
                deserialize = serializer.getClass().getMethod("deserialize", String.class);
                Class<?> comp = Class.forName("net.kyori.adventure.text.Component");
                return new AdventureRuntime(comp, serializer, deserialize);
            } catch (ReflectiveOperationException | LinkageError e) {
                return null;
            }
        }
    }
}
