package org.neko.walkorbit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.util.Vector;
import org.neko.walkorbit.menu.MenuScreen;
import org.neko.walkorbit.menu.MenuScreen.Button;
import org.neko.walkorbit.menu.MenuScreen.Choice;
import org.neko.walkorbit.menu.MenuScreen.Input;
import org.neko.walkorbit.menu.MenuScreen.Item;
import org.neko.walkorbit.menu.MenuScreen.Slider;
import org.neko.walkorbit.menu.MenuScreen.TextField;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The custom-orbit builder. Its screens are {@link MenuScreen}s, so they show as dialogs when the
 * player can get them and as chests otherwise, with the same icons, labels and tooltips.
 *
 * <ol>
 *   <li>Main: name preview, the combination being edited, name field, Add / Remove n / Save.
 *       Particles are used walking and standing by default; "Edit standing separately" switches to
 *       a separate standing list with its own add/remove.</li>
 *   <li>Picker: every selectable particle, with its texture as an icon in dialogs.</li>
 *   <li>Options: RGB sliders, a hex field and a 16-colour choice for coloured particles, plus Preview
 *       (values only reach the server on a button press, so the colour is shown then).</li>
 * </ol>
 * What the player has typed is kept here (the draft), so it survives previews, errors and switching
 * between dialog and chest. A name and at least one particle are required to save.
 */
public final class OrbitBuilder {
    public static final String PERMISSION = "walkorbit.create";
    static final int MAX_SLOTS = 5;
    private static final long SESSION_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    /** The 16 chat colours: code, RGB, and a dye for the chest icon. */
    private static final String[][] PALETTE = {
            {"0", "000000", "BLACK_DYE"}, {"1", "0000AA", "BLUE_DYE"}, {"2", "00AA00", "GREEN_DYE"},
            {"3", "00AAAA", "CYAN_DYE"}, {"4", "AA0000", "RED_DYE"}, {"5", "AA00AA", "PURPLE_DYE"},
            {"6", "FFAA00", "ORANGE_DYE"}, {"7", "AAAAAA", "LIGHT_GRAY_DYE"}, {"8", "555555", "GRAY_DYE"},
            {"9", "5555FF", "LIGHT_BLUE_DYE"}, {"a", "55FF55", "LIME_DYE"}, {"b", "55FFFF", "LIGHT_BLUE_DYE"},
            {"c", "FF5555", "RED_DYE"}, {"d", "FF55FF", "MAGENTA_DYE"}, {"e", "FFFF55", "YELLOW_DYE"},
            {"f", "FFFFFF", "WHITE_DYE"},
    };
    private static final String NO_PALETTE = "none";

    static final class Slot {
        Particle particle;
        Color color;
        Color color2;
        float size = 1f;
        String material;

        Slot copy() {
            Slot s = new Slot();
            s.particle = particle;
            s.color = color;
            s.color2 = color2;
            s.size = size;
            s.material = material;
            return s;
        }
    }

    /** What is typed on the options screen. */
    static final class Draft {
        float r = 255, g = 255, b = 255, size = 1f;
        String hex = "", palette = NO_PALETTE, hex2 = "", palette2 = NO_PALETTE;
        String block = "STONE", item = "DIAMOND";
    }

    static final class Session {
        final List<Slot> walk = new ArrayList<>();
        final List<Slot> idle = new ArrayList<>();
        boolean idleSeparate;
        boolean editingIdle;
        String name = "";
        String error;
        // options screen
        Particle particle;
        Draft draft = new Draft();
        Slot preview;
        String optError;
        long lastUsed = System.currentTimeMillis();

        List<Slot> editing() {
            return editingIdle ? idle : walk;
        }

        List<Slot> idleEffective() {
            return idleSeparate ? idle : walk;
        }
    }

    private final WalkOrbit plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public OrbitBuilder(WalkOrbit plugin) {
        this.plugin = plugin;
        // Unfinished work is dropped after a while (closing a dialog with Esc tells the server nothing)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            sessions.values().removeIf(s -> now - s.lastUsed > SESSION_TIMEOUT_MS);
        }, 1200L, 1200L);
    }

    public void open(Player p) {
        if (!p.hasPermission(PERMISSION)) {
            p.sendMessage(lm().getString("command.no_permission", p));
            return;
        }
        showMain(p);
    }

    public void forget(UUID id) {
        sessions.remove(id);
    }

    // ------------------------------------------------------------------ navigation

    /** Every screen goes through here, so a player who lost the permission meanwhile gets nothing. */
    private void show(Player p, java.util.function.Supplier<MenuScreen> screen) {
        if (!p.isOnline() || !p.hasPermission(PERMISSION)) return;
        session(p).lastUsed = System.currentTimeMillis();
        plugin.getMenus().open(p, screen);
    }

    private void showMain(Player p) {
        show(p, () -> mainScreen(p));
    }

    private void showPicker(Player p) {
        show(p, () -> pickerScreen(p));
    }

    private void showOptions(Player p) {
        show(p, () -> optionsScreen(p));
    }

    // ------------------------------------------------------------------ main screen

    private MenuScreen mainScreen(Player p) {
        Session s = session(p);
        List<Slot> list = s.editing();

        List<Component> header = new ArrayList<>();
        if (s.error != null) {
            header.add(legacy(s.error));
            s.error = null;
        }
        header.add(s.name.isBlank() ? text(p, "builder.no_name")
                : text(p, "builder.preview").append(legacy(colorize(s.name))));
        String headerKey = s.editingIdle ? "builder.combo_idle" : s.idleSeparate ? "builder.combo_walk" : "builder.combo_both";
        header.add(text(p, headerKey, Map.of("n", String.valueOf(list.size()), "max", String.valueOf(MAX_SLOTS))));
        if (list.isEmpty()) header.add(text(p, "builder.empty"));
        if (!s.editingIdle && s.idleSeparate) {
            header.add(text(p, "builder.idle_summary", Map.of("n", String.valueOf(s.idle.size()))));
        }

        List<Item> items = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Slot slot = list.get(i);
            items.add(new Item(slotIcon(slot), ParticleCatalog.sprite(slot.particle), slotLine(i, slot)));
        }

        List<Input> inputs = List.of(new TextField("name", text(p, "builder.name"), new ItemStack(Material.NAME_TAG),
                64, () -> s.name, v -> s.name = v));

        List<Button> buttons = new ArrayList<>();
        if (list.size() < MAX_SLOTS) {
            buttons.add(new Button(text(p, "builder.add"), tips(p, "builder.add_hint"),
                    new ItemStack(Material.LIME_DYE), this::showPicker));
        }
        for (int i = 0; i < list.size(); i++) {
            int index = i;
            buttons.add(new Button(text(p, "builder.remove", Map.of("n", String.valueOf(i + 1))),
                    tips(p, "builder.remove_hint"), slotIcon(list.get(i)), pl -> {
                List<Slot> l = session(pl).editing();
                if (index < l.size()) l.remove(index);
                showMain(pl);
            }));
        }
        if (!s.editingIdle) {
            buttons.add(new Button(text(p, s.idleSeparate ? "builder.edit_idle_again" : "builder.edit_idle"),
                    tips(p, "builder.edit_idle_hint"), new ItemStack(Material.CLOCK), pl -> {
                Session ss = session(pl);
                if (!ss.idleSeparate) {
                    // start the standing list as a copy of the walking one
                    ss.idle.clear();
                    for (Slot slot : ss.walk) ss.idle.add(slot.copy());
                    ss.idleSeparate = true;
                }
                ss.editingIdle = true;
                showMain(pl);
            }));
        } else {
            buttons.add(new Button(text(p, "builder.edit_walk"), tips(p, "builder.edit_walk_hint"),
                    new ItemStack(Material.LEATHER_BOOTS), pl -> {
                session(pl).editingIdle = false;
                showMain(pl);
            }));
            buttons.add(new Button(text(p, "builder.idle_same"), tips(p, "builder.idle_same_hint"),
                    new ItemStack(Material.PAPER), pl -> {
                Session ss = session(pl);
                ss.idle.clear();
                ss.idleSeparate = false;
                ss.editingIdle = false;
                showMain(pl);
            }));
        }
        buttons.add(new Button(text(p, "builder.save"), tips(p, "builder.save_hint"),
                new ItemStack(Material.WRITABLE_BOOK), this::trySave));

        Button cancel = new Button(text(p, "builder.cancel"), tips(p, "builder.cancel_hint"),
                new ItemStack(Material.BARRIER), pl -> forget(pl.getUniqueId()));

        return new MenuScreen(text(p, "builder.title"), header, items, inputs, buttons, cancel);
    }

    // ------------------------------------------------------------------ particle picker

    private MenuScreen pickerScreen(Player p) {
        Session s = session(p);
        List<Button> buttons = new ArrayList<>();
        for (Particle particle : ParticleCatalog.selectable()) {
            ParticleCatalog.Kind kind = ParticleCatalog.kindOf(particle);
            List<Component> tip = new ArrayList<>();
            if (kind.colored()) tip.add(text(p, "builder.tip_color"));
            else if (kind == ParticleCatalog.Kind.BLOCK) tip.add(text(p, "builder.tip_block"));
            else if (kind == ParticleCatalog.Kind.ITEM) tip.add(text(p, "builder.tip_item"));
            tip.add(text(p, "builder.pick_hint"));
            buttons.add(new Button(Component.text(ParticleCatalog.label(particle)), ParticleCatalog.sprite(particle),
                    tip, new ItemStack(ParticleCatalog.icon(particle)), pl -> {
                Session ss = session(pl);
                ss.particle = particle;
                ss.draft = new Draft();
                ss.preview = null;
                ss.optError = null;
                showOptions(pl);
            }));
        }
        Button back = new Button(text(p, "builder.back"), List.of(), new ItemStack(Material.ARROW), this::showMain);
        List<Component> header = List.of(text(p, s.editingIdle ? "builder.pick_body_idle" : "builder.pick_body"));
        return new MenuScreen(text(p, "builder.pick_title"), header, List.of(), List.of(), buttons, back);
    }

    // ------------------------------------------------------------------ options for one particle

    private MenuScreen optionsScreen(Player p) {
        Session s = session(p);
        Particle particle = s.particle;
        Draft d = s.draft;
        ParticleCatalog.Kind kind = ParticleCatalog.kindOf(particle);

        List<Component> header = new ArrayList<>();
        if (s.preview != null && s.preview.color != null) {
            Component sw = text(p, "builder.preview_now").append(swatch(s.preview.color));
            if (s.preview.color2 != null && !s.preview.color2.equals(s.preview.color)) {
                sw = sw.append(Component.text("→ ")).append(swatch(s.preview.color2));
            }
            header.add(sw);
        }
        if (s.optError != null) {
            header.add(legacy(s.optError));
            s.optError = null;
        }

        ItemStack icon = s.preview != null && s.preview.color != null ? potion(s.preview.color)
                : new ItemStack(ParticleCatalog.icon(particle));
        List<Item> items = List.of(new Item(icon, ParticleCatalog.sprite(particle),
                Component.text(ParticleCatalog.label(particle))));

        List<Input> inputs = new ArrayList<>();
        if (kind.colored()) {
            inputs.add(new Slider("r", text(p, "builder.red"), new ItemStack(Material.RED_DYE), 0, 255, 1, 16, () -> d.r, v -> d.r = v));
            inputs.add(new Slider("g", text(p, "builder.green"), new ItemStack(Material.LIME_DYE), 0, 255, 1, 16, () -> d.g, v -> d.g = v));
            inputs.add(new Slider("b", text(p, "builder.blue"), new ItemStack(Material.BLUE_DYE), 0, 255, 1, 16, () -> d.b, v -> d.b = v));
            inputs.add(new TextField("hex", text(p, "builder.hex"), new ItemStack(Material.NAME_TAG), 7, () -> d.hex, v -> d.hex = v));
            inputs.add(new Choice("palette", text(p, "builder.palette"), new ItemStack(Material.WHITE_DYE),
                    paletteOptions(p), () -> d.palette, v -> d.palette = v));
        }
        if (kind == ParticleCatalog.Kind.TRANSITION) {
            inputs.add(new TextField("hex2", text(p, "builder.hex2"), new ItemStack(Material.NAME_TAG), 7, () -> d.hex2, v -> d.hex2 = v));
            inputs.add(new Choice("palette2", text(p, "builder.palette2"), new ItemStack(Material.WHITE_DYE),
                    paletteOptions(p), () -> d.palette2, v -> d.palette2 = v));
        }
        if (ParticleCatalog.hasSize(particle)) {
            inputs.add(new Slider("size", text(p, "builder.size"), new ItemStack(Material.SLIME_BALL), 0.5f, 3f, 0.1f, 0.5f,
                    () -> d.size, v -> d.size = v));
        }
        if (kind == ParticleCatalog.Kind.BLOCK) {
            inputs.add(new TextField("block", text(p, "builder.block"), new ItemStack(Material.STONE), 64, () -> d.block, v -> d.block = v));
        }
        if (kind == ParticleCatalog.Kind.ITEM) {
            inputs.add(new TextField("item", text(p, "builder.item"), new ItemStack(Material.DIAMOND), 64, () -> d.item, v -> d.item = v));
        }

        List<Component> confirmTip = new ArrayList<>(tips(p, "builder.confirm_hint"));
        List<Component> previewTip = new ArrayList<>(tips(p, "builder.preview_hint"));
        if (kind.colored()) {
            confirmTip.add(text(p, "builder.color_order"));
            previewTip.add(text(p, "builder.color_order"));
        }
        List<Button> buttons = List.of(
                new Button(text(p, "builder.confirm"), confirmTip, new ItemStack(Material.LIME_CONCRETE), pl -> {
                    Session ss = session(pl);
                    Slot slot = parse(pl, ss);
                    if (slot == null) {
                        showOptions(pl);
                        return;
                    }
                    List<Slot> l = ss.editing();
                    if (l.size() < MAX_SLOTS) l.add(slot);
                    showMain(pl);
                }),
                new Button(text(p, kind.colored() ? "builder.preview_color" : "builder.preview_plain"), previewTip,
                        new ItemStack(Material.SPYGLASS), pl -> {
                    Session ss = session(pl);
                    Slot slot = parse(pl, ss);
                    if (slot != null) {
                        showPreview(pl, slot);
                        ss.preview = slot;
                    }
                    showOptions(pl);
                }));
        Button back = new Button(text(p, "builder.back"), List.of(), new ItemStack(Material.ARROW), this::showPicker);

        return new MenuScreen(text(p, "builder.opt_title", Map.of("particle", ParticleCatalog.label(particle))),
                header, items, inputs, buttons, back);
    }

    /** Reads the draft into a slot. On invalid input sets {@code optError} and returns null. */
    private Slot parse(Player p, Session s) {
        Particle particle = s.particle;
        Draft d = s.draft;
        ParticleCatalog.Kind kind = ParticleCatalog.kindOf(particle);
        Slot slot = new Slot();
        slot.particle = particle;

        if (kind.colored()) {
            slot.color = resolveColor(d.palette, d.hex, d, true);
            if (slot.color == null) {
                s.optError = lm().getString("builder.err_hex", p);
                return null;
            }
        }
        if (kind == ParticleCatalog.Kind.TRANSITION) {
            Color c2 = resolveColor(d.palette2, d.hex2, d, false);
            if (c2 == null && !blank(d.hex2)) {
                s.optError = lm().getString("builder.err_hex", p);
                return null;
            }
            slot.color2 = c2 != null ? c2 : slot.color;
        }
        if (ParticleCatalog.hasSize(particle)) slot.size = Math.round(d.size * 10f) / 10f;
        if (kind == ParticleCatalog.Kind.BLOCK) {
            Material m = Material.matchMaterial(String.valueOf(d.block).trim());
            if (m == null || !m.isBlock()) {
                s.optError = lm().getString("builder.err_block", p);
                return null;
            }
            slot.material = m.name();
        }
        if (kind == ParticleCatalog.Kind.ITEM) {
            Material m = Material.matchMaterial(String.valueOf(d.item).trim());
            if (m == null || !m.isItem() || m.isAir()) {
                s.optError = lm().getString("builder.err_item", p);
                return null;
            }
            slot.material = m.name();
        }
        return slot;
    }

    /** Shows the particle for about 2 seconds as a short vertical line in front of the player. */
    private void showPreview(Player p, Slot slot) {
        Map<String, Object> map = entry(slot, true);
        map.remove("chance");
        map.put("linger", 40);
        map.put("refresh", 4);
        map.put("fall", slot.particle == Particle.TRAIL ? 0.3 : 0.0);
        ParticleEffect effect = ParticleEffect.from(map, "preview", plugin.getLogger());
        if (effect == null) return;

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
        Location c = eye.clone().add(dir.normalize().multiply(2.2));
        for (int i = 0; i < 6; i++) {
            effect.spawn(c.getWorld(), c.getX(), c.getY() - 0.6 + i * 0.24, c.getZ());
        }
    }

    /**
     * 16-colour choice wins, then a valid hex code, then (if allowed) the sliders.
     * Returns null when a hex code was typed but is invalid, or when nothing applies.
     */
    private static Color resolveColor(String palette, String hex, Draft d, boolean sliders) {
        if (palette != null && !palette.equals(NO_PALETTE)) {
            for (String[] c : PALETTE) if (c[0].equals(palette)) return Color.fromRGB(Integer.parseInt(c[1], 16));
        }
        if (!blank(hex)) {
            String h = hex.trim();
            if (h.startsWith("#")) h = h.substring(1);
            if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            if (!h.matches("[0-9A-Fa-f]{6}")) return null;
            return Color.fromRGB(Integer.parseInt(h, 16));
        }
        if (!sliders) return null;
        return Color.fromRGB(channel(d.r), channel(d.g), channel(d.b));
    }

    private static int channel(float f) {
        return Math.max(0, Math.min(255, Math.round(f)));
    }

    private List<Choice.Option> paletteOptions(Player p) {
        List<Choice.Option> list = new ArrayList<>();
        list.add(new Choice.Option(NO_PALETTE, text(p, "builder.palette_none")));
        for (String[] c : PALETTE) {
            Component label = Component.text("■■■ ").color(TextColor.fromHexString("#" + c[1]))
                    .append(Component.text("&" + c[0] + "  #" + c[1]).color(TextColor.color(0xAAAAAA)));
            list.add(new Choice.Option(c[0], label));
        }
        return list;
    }

    // ------------------------------------------------------------------ save

    private void trySave(Player p) {
        Session s = session(p);
        String colored = colorize(s.name.trim());
        String plain = ChatColor.stripColor(colored).trim();
        if (plain.isEmpty()) {
            s.error = lm().getString("builder.err_name", p);
            showMain(p);
            return;
        }
        List<Slot> idleList = s.idleEffective();
        if (s.walk.isEmpty() && idleList.isEmpty()) {
            s.error = lm().getString("builder.err_empty", p);
            showMain(p);
            return;
        }

        List<Map<String, Object>> walk = new ArrayList<>();
        List<Map<String, Object>> idle = new ArrayList<>();
        for (Slot slot : s.walk) walk.add(entry(slot, true));
        for (Slot slot : idleList) idle.add(entry(slot, false));
        String id = makeId(plain);
        Slot first = !s.walk.isEmpty() ? s.walk.get(0) : idleList.get(0);
        String icon = ParticleCatalog.kindOf(first.particle).colored() ? Material.POTION.name()
                : ParticleCatalog.icon(first.particle).name();

        if (!plugin.getCustomStore().add(id, s.name.trim(), icon, p.getName(), walk, idle)) {
            s.error = lm().getString("builder.err_save", p);
            showMain(p);
            return;
        }
        plugin.loadGroups();
        forget(p.getUniqueId());
        p.sendMessage(lm().getString("builder.saved", p, Map.of("name", colored + ChatColor.RESET, "id", id)));
    }

    /** One config entry, in the same format as config.yml. */
    private static Map<String, Object> entry(Slot slot, boolean walk) {
        Particle particle = slot.particle;
        ParticleCatalog.Kind kind = ParticleCatalog.kindOf(particle);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("particle", particle.name());
        if (kind.colored()) m.put("color", hex(slot.color));
        if (kind == ParticleCatalog.Kind.TRANSITION) m.put("to_color", hex(slot.color2));
        if (ParticleCatalog.hasSize(particle)) m.put("size", (double) slot.size);
        if (kind == ParticleCatalog.Kind.BLOCK) m.put("block", slot.material);
        if (kind == ParticleCatalog.Kind.ITEM) m.put("item", slot.material);
        if (kind == ParticleCatalog.Kind.TRAIL) {
            m.put("duration", walk ? 25 : 30);
            m.put("fall", walk ? 0.25 : 0.5);
        }
        if (particle == Particle.NOTE) m.put("note", "random");
        // Coloured particles form the band itself; everything else is sprinkled in like the built-in orbits
        if (!kind.colored()) m.put("chance", 0.3);
        if (ParticleCatalog.shortLived(particle)) {
            m.put("linger", walk ? 20 : 24);
            m.put("refresh", 4);
            m.put("fall", walk ? 0.2 : 0.45);
        }
        return m;
    }

    /** "My Orbit!" -> CUSTOM_MY_ORBIT; names with no ASCII letters/digits -> CUSTOM, CUSTOM_2, ... */
    private String makeId(String plain) {
        String base = plain.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        base = base.isEmpty() ? "CUSTOM" : "CUSTOM_" + base;
        String id = base;
        for (int n = 2; plugin.getGroups().containsKey(id) || plugin.getCustomStore().contains(id); n++) {
            id = base + "_" + n;
        }
        return id;
    }

    // ------------------------------------------------------------------ helpers

    private Session session(Player p) {
        return sessions.computeIfAbsent(p.getUniqueId(), k -> new Session());
    }

    private LocaleManager lm() {
        return plugin.getLocaleManager();
    }

    private Component text(Player p, String key) {
        return legacy(lm().getString(key, p));
    }

    private Component text(Player p, String key, Map<String, String> placeholders) {
        return legacy(lm().getString(key, p, placeholders));
    }

    private List<Component> tips(Player p, String key) {
        return List.of(text(p, key));
    }

    private static Component legacy(String sectionText) {
        return LegacyComponentSerializer.legacySection().deserialize(sectionText);
    }

    private static String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String hex(Color c) {
        return String.format("#%06X", c.asRGB());
    }

    private static ItemStack potion(Color color) {
        ItemStack potion = new ItemStack(Material.POTION);
        if (potion.getItemMeta() instanceof PotionMeta meta) {
            meta.setColor(color);
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private ItemStack slotIcon(Slot slot) {
        return slot.color != null ? potion(slot.color) : new ItemStack(ParticleCatalog.icon(slot.particle));
    }

    private Component slotLine(int index, Slot slot) {
        Component line = Component.text((index + 1) + ". " + ParticleCatalog.label(slot.particle) + " ");
        if (slot.color != null) line = line.append(swatch(slot.color));
        if (slot.color2 != null && !slot.color2.equals(slot.color)) line = line.append(Component.text("→")).append(swatch(slot.color2));
        if (slot.material != null) line = line.append(Component.text(slot.material.toLowerCase(Locale.ROOT) + " "));
        return line;
    }

    private static Component swatch(Color c) {
        return Component.text("■ " + hex(c) + " ").color(TextColor.color(c.asRGB()));
    }
}
