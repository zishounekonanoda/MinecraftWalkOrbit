package org.neko.walkorbit;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The custom-orbit builder, built on Minecraft dialogs (1.21.6+).
 *
 * <ol>
 *   <li>Main screen: name preview, the current combination (icons), a name field, and
 *       Add / Remove n / Save buttons. Particles are used both walking and standing by default;
 *       "Edit standing separately" switches to a separate standing list with its own add/remove.</li>
 *   <li>Picker: every selectable particle as a button with its texture as an icon.</li>
 *   <li>Options: for coloured particles, RGB sliders, a hex field and a 16-colour choice on the same
 *       screen, plus "Preview" (dialogs only report values on a button press, so the colour is shown
 *       then: a swatch on the screen and the real particle in front of the player).
 *       "Confirm" adds it to the list being edited.</li>
 * </ol>
 * A name and at least one particle are required to save. Saved orbits go to custom_orbits.yml and
 * appear in everyone's menu.
 */
public final class OrbitBuilder {
    public static final String PERMISSION = "walkorbit.create";
    static final int MAX_SLOTS = 5;

    private static final ClickCallback.Options CALLBACK = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(30)).build();

    /** The 16 chat colours: code and RGB. */
    private static final String[][] PALETTE = {
            {"0", "000000"}, {"1", "0000AA"}, {"2", "00AA00"}, {"3", "00AAAA"},
            {"4", "AA0000"}, {"5", "AA00AA"}, {"6", "FFAA00"}, {"7", "AAAAAA"},
            {"8", "555555"}, {"9", "5555FF"}, {"a", "55FF55"}, {"b", "55FFFF"},
            {"c", "FF5555"}, {"d", "FF55FF"}, {"e", "FFFF55"}, {"f", "FFFFFF"},
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

    /** What was typed on the options screen, so it survives a preview / an error. */
    static final class Draft {
        float r = 255, g = 255, b = 255, size = 1f;
        String hex = "", palette = NO_PALETTE, hex2 = "", palette2 = NO_PALETTE;
        String block = "STONE", item = "DIAMOND";

        static Draft from(DialogResponseView view) {
            Draft d = new Draft();
            d.r = orElse(view.getFloat("r"), 255f);
            d.g = orElse(view.getFloat("g"), 255f);
            d.b = orElse(view.getFloat("b"), 255f);
            d.size = orElse(view.getFloat("size"), 1f);
            d.hex = orElse(view.getText("hex"), "");
            d.palette = orElse(view.getText("palette"), NO_PALETTE);
            d.hex2 = orElse(view.getText("hex2"), "");
            d.palette2 = orElse(view.getText("palette2"), NO_PALETTE);
            d.block = orElse(view.getText("block"), "STONE");
            d.item = orElse(view.getText("item"), "DIAMOND");
            return d;
        }

        private static <T> T orElse(T v, T def) {
            return v == null ? def : v;
        }
    }

    static final class Session {
        final List<Slot> walk = new ArrayList<>();
        final List<Slot> idle = new ArrayList<>();
        /** false: standing uses the same particles as walking. */
        boolean idleSeparate;
        /** true while the standing list is the one being edited. */
        boolean editingIdle;
        String name = "";
        String error;

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
    }

    public void open(Player p) {
        if (!p.hasPermission(PERMISSION)) {
            p.sendMessage(lm().getString("command.no_permission", p));
            return;
        }
        p.closeInventory();
        openMain(p);
    }

    public void forget(UUID id) {
        sessions.remove(id);
    }

    // ------------------------------------------------------------------ main screen

    private void openMain(Player p) {
        Session s = session(p);
        List<Slot> list = s.editing();
        List<DialogBody> body = new ArrayList<>();
        if (s.error != null) {
            body.add(DialogBody.plainMessage(legacy(s.error)));
            s.error = null;
        }
        body.add(DialogBody.plainMessage(s.name.isBlank()
                ? text(p, "builder.no_name")
                : text(p, "builder.preview").append(legacy(colorize(s.name)))));

        String headerKey = s.editingIdle ? "builder.combo_idle" : s.idleSeparate ? "builder.combo_walk" : "builder.combo_both";
        body.add(DialogBody.plainMessage(text(p, headerKey,
                Map.of("n", String.valueOf(list.size()), "max", String.valueOf(MAX_SLOTS)))));
        if (list.isEmpty()) body.add(DialogBody.plainMessage(text(p, "builder.empty")));
        for (int i = 0; i < list.size(); i++) {
            Slot slot = list.get(i);
            body.add(DialogBody.item(slotIcon(slot))
                    .description(DialogBody.plainMessage(slotLine(i, slot)))
                    .showTooltip(false)
                    .build());
        }
        if (!s.editingIdle && s.idleSeparate) {
            body.add(DialogBody.plainMessage(text(p, "builder.idle_summary", Map.of("n", String.valueOf(s.idle.size())))));
        }

        List<DialogInput> inputs = List.of(DialogInput.text("name", text(p, "builder.name"))
                .initial(s.name).maxLength(64).width(300).build());

        List<ActionButton> buttons = new ArrayList<>();
        if (list.size() < MAX_SLOTS) {
            buttons.add(button(text(p, "builder.add"), null, action(p, (view, pl) -> {
                captureName(pl, view);
                openPicker(pl);
            })));
        }
        for (int i = 0; i < list.size(); i++) {
            int index = i;
            buttons.add(button(text(p, "builder.remove", Map.of("n", String.valueOf(i + 1))), null, action(p, (view, pl) -> {
                captureName(pl, view);
                List<Slot> l = session(pl).editing();
                if (index < l.size()) l.remove(index);
                openMain(pl);
            })));
        }

        if (!s.editingIdle) {
            // First time: start the standing list as a copy of the walking one, then edit it on its own
            buttons.add(button(text(p, s.idleSeparate ? "builder.edit_idle_again" : "builder.edit_idle"),
                    text(p, "builder.edit_idle_hint"), action(p, (view, pl) -> {
                        captureName(pl, view);
                        Session ss = session(pl);
                        if (!ss.idleSeparate) {
                            ss.idle.clear();
                            for (Slot slot : ss.walk) ss.idle.add(slot.copy());
                            ss.idleSeparate = true;
                        }
                        ss.editingIdle = true;
                        openMain(pl);
                    })));
        } else {
            buttons.add(button(text(p, "builder.edit_walk"), null, action(p, (view, pl) -> {
                captureName(pl, view);
                session(pl).editingIdle = false;
                openMain(pl);
            })));
            buttons.add(button(text(p, "builder.idle_same"), null, action(p, (view, pl) -> {
                captureName(pl, view);
                Session ss = session(pl);
                ss.idle.clear();
                ss.idleSeparate = false;
                ss.editingIdle = false;
                openMain(pl);
            })));
        }

        buttons.add(button(text(p, "builder.save"), text(p, "builder.save_hint"), action(p, (view, pl) -> {
            captureName(pl, view);
            trySave(pl);
        })));
        ActionButton cancel = button(text(p, "builder.cancel"), null, action(p, (view, pl) -> forget(pl.getUniqueId())));

        show(p, DialogBase.builder(text(p, "builder.title")).canCloseWithEscape(true).body(body).inputs(inputs).build(),
                DialogType.multiAction(buttons).columns(2).exitAction(cancel).build());
    }

    private void captureName(Player p, DialogResponseView view) {
        String n = view.getText("name");
        if (n != null) session(p).name = n;
    }

    // ------------------------------------------------------------------ particle picker

    private void openPicker(Player p) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Particle particle : ParticleCatalog.selectable()) {
            ParticleCatalog.Kind kind = ParticleCatalog.kindOf(particle);
            Component tip = kind.colored() ? text(p, "builder.tip_color")
                    : kind == ParticleCatalog.Kind.BLOCK ? text(p, "builder.tip_block")
                    : kind == ParticleCatalog.Kind.ITEM ? text(p, "builder.tip_item")
                    : null;
            buttons.add(ActionButton.builder(ParticleCatalog.sprite(particle).append(Component.text(" " + ParticleCatalog.label(particle))))
                    .tooltip(tip).width(110)
                    .action(action(p, (view, pl) -> openOptions(pl, particle, null, new Draft(), null)))
                    .build());
        }
        ActionButton back = button(text(p, "builder.back"), null, action(p, (view, pl) -> openMain(pl)));
        String bodyKey = session(p).editingIdle ? "builder.pick_body_idle" : "builder.pick_body";
        show(p, DialogBase.builder(text(p, "builder.pick_title")).canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(text(p, bodyKey)))).build(),
                DialogType.multiAction(buttons).columns(4).exitAction(back).build());
    }

    // ------------------------------------------------------------------ options for one particle

    /**
     * @param preview the slot last previewed (shown as a swatch), or null
     */
    private void openOptions(Player p, Particle particle, String error, Draft d, Slot preview) {
        ParticleCatalog.Kind kind = ParticleCatalog.kindOf(particle);
        List<DialogBody> body = new ArrayList<>();
        ItemStack icon = preview != null && preview.color != null ? potion(preview.color) : new ItemStack(ParticleCatalog.icon(particle));
        body.add(DialogBody.item(icon)
                .description(DialogBody.plainMessage(ParticleCatalog.sprite(particle)
                        .append(Component.text(" " + ParticleCatalog.label(particle)))))
                .showTooltip(false).build());
        if (preview != null && preview.color != null) {
            Component swatch = text(p, "builder.preview_now").append(swatch(preview.color));
            if (preview.color2 != null && !preview.color2.equals(preview.color)) {
                swatch = swatch.append(Component.text("→ ")).append(swatch(preview.color2));
            }
            body.add(DialogBody.plainMessage(swatch));
        }
        if (error != null) body.add(DialogBody.plainMessage(legacy(error)));

        List<DialogInput> inputs = new ArrayList<>();
        if (kind.colored()) {
            inputs.add(DialogInput.numberRange("r", text(p, "builder.red"), 0, 255).step(1f).initial(d.r).width(300).build());
            inputs.add(DialogInput.numberRange("g", text(p, "builder.green"), 0, 255).step(1f).initial(d.g).width(300).build());
            inputs.add(DialogInput.numberRange("b", text(p, "builder.blue"), 0, 255).step(1f).initial(d.b).width(300).build());
            inputs.add(DialogInput.text("hex", text(p, "builder.hex")).initial(d.hex).maxLength(7).width(300).build());
            inputs.add(DialogInput.singleOption("palette", text(p, "builder.palette"), paletteEntries(p, d.palette)).width(300).build());
        }
        if (kind == ParticleCatalog.Kind.TRANSITION) {
            inputs.add(DialogInput.text("hex2", text(p, "builder.hex2")).initial(d.hex2).maxLength(7).width(300).build());
            inputs.add(DialogInput.singleOption("palette2", text(p, "builder.palette2"), paletteEntries(p, d.palette2)).width(300).build());
        }
        if (ParticleCatalog.hasSize(particle)) {
            inputs.add(DialogInput.numberRange("size", text(p, "builder.size"), 0.5f, 3f).step(0.1f).initial(d.size).width(300).build());
        }
        if (kind == ParticleCatalog.Kind.BLOCK) {
            inputs.add(DialogInput.text("block", text(p, "builder.block")).initial(d.block).maxLength(64).width(300).build());
        }
        if (kind == ParticleCatalog.Kind.ITEM) {
            inputs.add(DialogInput.text("item", text(p, "builder.item")).initial(d.item).maxLength(64).width(300).build());
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(text(p, "builder.confirm"), null, action(p, (view, pl) -> {
            Draft draft = Draft.from(view);
            Slot slot = parse(pl, particle, view, draft);
            if (slot == null) return; // parse reopened the screen with the error
            List<Slot> list = session(pl).editing();
            if (list.size() < MAX_SLOTS) list.add(slot);
            openMain(pl);
        })));
        buttons.add(button(text(p, "builder.preview_color"), text(p, "builder.preview_hint"), action(p, (view, pl) -> {
            Draft draft = Draft.from(view);
            Slot slot = parse(pl, particle, view, draft);
            if (slot == null) return;
            showPreview(pl, slot);
            openOptions(pl, particle, null, draft, slot);
        })));
        ActionButton back = button(text(p, "builder.back"), null, action(p, (view, pl) -> openPicker(pl)));

        show(p, DialogBase.builder(text(p, "builder.opt_title", Map.of("particle", ParticleCatalog.label(particle))))
                        .canCloseWithEscape(true).body(body).inputs(inputs).build(),
                DialogType.multiAction(buttons).columns(2).exitAction(back).build());
    }

    /** Reads the options screen into a slot; on invalid input reopens the screen with the error and returns null. */
    private Slot parse(Player p, Particle particle, DialogResponseView view, Draft draft) {
        ParticleCatalog.Kind kind = ParticleCatalog.kindOf(particle);
        Slot slot = new Slot();
        slot.particle = particle;

        if (kind.colored()) {
            slot.color = resolveColor(view, "palette", "hex", true);
            if (slot.color == null) {
                openOptions(p, particle, lm().getString("builder.err_hex", p), draft, null);
                return null;
            }
        }
        if (kind == ParticleCatalog.Kind.TRANSITION) {
            boolean hex2Given = !blank(view.getText("hex2"));
            Color c2 = resolveColor(view, "palette2", "hex2", false);
            if (c2 == null && hex2Given) {
                openOptions(p, particle, lm().getString("builder.err_hex", p), draft, null);
                return null;
            }
            slot.color2 = c2 != null ? c2 : slot.color;
        }
        if (ParticleCatalog.hasSize(particle)) {
            Float size = view.getFloat("size");
            slot.size = size == null ? 1f : Math.round(size * 10f) / 10f;
        }
        if (kind == ParticleCatalog.Kind.BLOCK) {
            Material m = Material.matchMaterial(String.valueOf(view.getText("block")).trim());
            if (m == null || !m.isBlock()) {
                openOptions(p, particle, lm().getString("builder.err_block", p), draft, null);
                return null;
            }
            slot.material = m.name();
        }
        if (kind == ParticleCatalog.Kind.ITEM) {
            Material m = Material.matchMaterial(String.valueOf(view.getText("item")).trim());
            if (m == null || !m.isItem() || m.isAir()) {
                openOptions(p, particle, lm().getString("builder.err_item", p), draft, null);
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
        map.put("fall", 0.0);
        if (slot.particle == Particle.TRAIL) map.put("fall", 0.3);
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
    private static Color resolveColor(DialogResponseView view, String paletteKey, String hexKey, boolean sliders) {
        String pal = view.getText(paletteKey);
        if (pal != null && !pal.equals(NO_PALETTE)) {
            for (String[] c : PALETTE) if (c[0].equals(pal)) return Color.fromRGB(Integer.parseInt(c[1], 16));
        }
        String hex = view.getText(hexKey);
        if (!blank(hex)) {
            String h = hex.trim();
            if (h.startsWith("#")) h = h.substring(1);
            if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            if (!h.matches("[0-9A-Fa-f]{6}")) return null;
            return Color.fromRGB(Integer.parseInt(h, 16));
        }
        if (!sliders) return null;
        return Color.fromRGB(channel(view.getFloat("r")), channel(view.getFloat("g")), channel(view.getFloat("b")));
    }

    private static int channel(Float f) {
        return f == null ? 255 : Math.max(0, Math.min(255, Math.round(f)));
    }

    private List<SingleOptionDialogInput.OptionEntry> paletteEntries(Player p, String selected) {
        List<SingleOptionDialogInput.OptionEntry> list = new ArrayList<>();
        list.add(SingleOptionDialogInput.OptionEntry.create(NO_PALETTE, text(p, "builder.palette_none"), NO_PALETTE.equals(selected)));
        for (String[] c : PALETTE) {
            Component label = Component.text("■■■ ").color(TextColor.fromHexString("#" + c[1]))
                    .append(Component.text("&" + c[0] + "  #" + c[1]).color(TextColor.color(0xAAAAAA)));
            list.add(SingleOptionDialogInput.OptionEntry.create(c[0], label, c[0].equals(selected)));
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
            openMain(p);
            return;
        }
        List<Slot> idleList = s.idleEffective();
        if (s.walk.isEmpty() && idleList.isEmpty()) {
            s.error = lm().getString("builder.err_empty", p);
            openMain(p);
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
            openMain(p);
            return;
        }
        plugin.loadGroups();
        forget(p.getUniqueId());
        p.closeDialog();
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
        Component line = Component.text((index + 1) + ". ").append(ParticleCatalog.sprite(slot.particle))
                .append(Component.text(" " + ParticleCatalog.label(slot.particle) + " "));
        if (slot.color != null) line = line.append(swatch(slot.color));
        if (slot.color2 != null && !slot.color2.equals(slot.color)) line = line.append(Component.text("→")).append(swatch(slot.color2));
        if (slot.material != null) line = line.append(Component.text(slot.material.toLowerCase(Locale.ROOT) + " "));
        return line;
    }

    private static Component swatch(Color c) {
        return Component.text("■ " + hex(c) + " ").color(TextColor.color(c.asRGB()));
    }

    private ActionButton button(Component label, Component tooltip, DialogAction action) {
        return ActionButton.builder(label).tooltip(tooltip).width(150).action(action).build();
    }

    /** A button action that runs on the main thread, only for the same, still-permitted player. */
    private DialogAction action(Player owner, BiConsumer<DialogResponseView, Player> handler) {
        UUID id = owner.getUniqueId();
        return DialogAction.customClick((view, audience) -> {
            Runnable r = () -> {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.hasPermission(PERMISSION)) return;
                handler.accept(view, p);
            };
            if (Bukkit.isPrimaryThread()) r.run();
            else Bukkit.getScheduler().runTask(plugin, r);
        }, CALLBACK);
    }

    private static void show(Player p, DialogBase base, DialogType type) {
        Dialog dialog = Dialog.create(b -> b.empty().base(base).type(type));
        p.showDialog(dialog);
    }
}
