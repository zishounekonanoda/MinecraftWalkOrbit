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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

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
 *       Add / Remove n / Save buttons.</li>
 *   <li>Picker: every selectable particle as a button.</li>
 *   <li>Options: where it's used (walk / idle / both) and, for coloured particles, RGB sliders,
 *       a hex field and a 16-colour choice on the same screen. "Confirm" adds it to the combination.</li>
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

    enum Use { WALK, IDLE, BOTH }

    static final class Slot {
        Particle particle;
        Use use = Use.BOTH;
        Color color;
        Color color2;
        float size = 1f;
        String material;
    }

    static final class Session {
        final List<Slot> slots = new ArrayList<>();
        String name = "";
        String error;
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
        List<DialogBody> body = new ArrayList<>();
        if (s.error != null) {
            body.add(DialogBody.plainMessage(legacy(s.error)));
            s.error = null;
        }
        body.add(DialogBody.plainMessage(s.name.isBlank()
                ? text(p, "builder.no_name")
                : text(p, "builder.preview").append(legacy(colorize(s.name)))));
        body.add(DialogBody.plainMessage(text(p, "builder.combo",
                Map.of("n", String.valueOf(s.slots.size()), "max", String.valueOf(MAX_SLOTS)))));
        if (s.slots.isEmpty()) body.add(DialogBody.plainMessage(text(p, "builder.empty")));
        for (int i = 0; i < s.slots.size(); i++) {
            Slot slot = s.slots.get(i);
            body.add(DialogBody.item(slotIcon(slot))
                    .description(DialogBody.plainMessage(slotLine(p, i, slot)))
                    .showTooltip(false)
                    .build());
        }

        List<DialogInput> inputs = List.of(DialogInput.text("name", text(p, "builder.name"))
                .initial(s.name).maxLength(64).width(300).build());

        List<ActionButton> buttons = new ArrayList<>();
        if (s.slots.size() < MAX_SLOTS) {
            buttons.add(button(text(p, "builder.add"), null, action(p, (view, pl) -> {
                captureName(pl, view);
                openPicker(pl);
            })));
        }
        for (int i = 0; i < s.slots.size(); i++) {
            int index = i;
            buttons.add(button(text(p, "builder.remove", Map.of("n", String.valueOf(i + 1))), null, action(p, (view, pl) -> {
                captureName(pl, view);
                Session ss = session(pl);
                if (index < ss.slots.size()) ss.slots.remove(index);
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
            buttons.add(ActionButton.builder(Component.text(ParticleCatalog.label(particle)))
                    .tooltip(tip).width(110)
                    .action(action(p, (view, pl) -> openOptions(pl, particle, null)))
                    .build());
        }
        ActionButton back = button(text(p, "builder.back"), null, action(p, (view, pl) -> openMain(pl)));
        show(p, DialogBase.builder(text(p, "builder.pick_title")).canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(text(p, "builder.pick_body")))).build(),
                DialogType.multiAction(buttons).columns(4).exitAction(back).build());
    }

    // ------------------------------------------------------------------ options for one particle

    private void openOptions(Player p, Particle particle, String error) {
        ParticleCatalog.Kind kind = ParticleCatalog.kindOf(particle);
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.item(new ItemStack(ParticleCatalog.icon(particle)))
                .description(DialogBody.plainMessage(Component.text(ParticleCatalog.label(particle))))
                .showTooltip(false).build());
        if (error != null) body.add(DialogBody.plainMessage(legacy(error)));

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.singleOption("use", text(p, "builder.use"), List.of(
                SingleOptionDialogInput.OptionEntry.create(Use.WALK.name(), text(p, "builder.use_walk"), false),
                SingleOptionDialogInput.OptionEntry.create(Use.IDLE.name(), text(p, "builder.use_idle"), false),
                SingleOptionDialogInput.OptionEntry.create(Use.BOTH.name(), text(p, "builder.use_both"), true)
        )).width(300).build());

        if (kind.colored()) {
            inputs.add(DialogInput.numberRange("r", text(p, "builder.red"), 0, 255).step(1f).initial(255f).width(300).build());
            inputs.add(DialogInput.numberRange("g", text(p, "builder.green"), 0, 255).step(1f).initial(255f).width(300).build());
            inputs.add(DialogInput.numberRange("b", text(p, "builder.blue"), 0, 255).step(1f).initial(255f).width(300).build());
            inputs.add(DialogInput.text("hex", text(p, "builder.hex")).initial("").maxLength(7).width(300).build());
            inputs.add(DialogInput.singleOption("palette", text(p, "builder.palette"), paletteEntries(p)).width(300).build());
        }
        if (kind == ParticleCatalog.Kind.TRANSITION) {
            inputs.add(DialogInput.text("hex2", text(p, "builder.hex2")).initial("").maxLength(7).width(300).build());
            inputs.add(DialogInput.singleOption("palette2", text(p, "builder.palette2"), paletteEntries(p)).width(300).build());
        }
        if (ParticleCatalog.hasSize(particle)) {
            inputs.add(DialogInput.numberRange("size", text(p, "builder.size"), 0.5f, 3f).step(0.1f).initial(1f).width(300).build());
        }
        if (kind == ParticleCatalog.Kind.BLOCK) {
            inputs.add(DialogInput.text("block", text(p, "builder.block")).initial("STONE").maxLength(64).width(300).build());
        }
        if (kind == ParticleCatalog.Kind.ITEM) {
            inputs.add(DialogInput.text("item", text(p, "builder.item")).initial("DIAMOND").maxLength(64).width(300).build());
        }

        ActionButton confirm = button(text(p, "builder.confirm"), null, action(p, (view, pl) -> confirm(pl, particle, view)));
        ActionButton back = button(text(p, "builder.back"), null, action(p, (view, pl) -> openPicker(pl)));
        show(p, DialogBase.builder(text(p, "builder.opt_title", Map.of("particle", ParticleCatalog.label(particle))))
                        .canCloseWithEscape(true).body(body).inputs(inputs).build(),
                DialogType.confirmation(confirm, back));
    }

    private void confirm(Player p, Particle particle, DialogResponseView view) {
        ParticleCatalog.Kind kind = ParticleCatalog.kindOf(particle);
        Slot slot = new Slot();
        slot.particle = particle;
        try {
            slot.use = Use.valueOf(view.getText("use"));
        } catch (RuntimeException e) {
            slot.use = Use.BOTH;
        }

        if (kind.colored()) {
            slot.color = resolveColor(view, "palette", "hex", true);
            if (slot.color == null) {
                openOptions(p, particle, lm().getString("builder.err_hex", p));
                return;
            }
        }
        if (kind == ParticleCatalog.Kind.TRANSITION) {
            boolean hex2Given = !blank(view.getText("hex2"));
            Color c2 = resolveColor(view, "palette2", "hex2", false);
            if (c2 == null && hex2Given) {
                openOptions(p, particle, lm().getString("builder.err_hex", p));
                return;
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
                openOptions(p, particle, lm().getString("builder.err_block", p));
                return;
            }
            slot.material = m.name();
        }
        if (kind == ParticleCatalog.Kind.ITEM) {
            Material m = Material.matchMaterial(String.valueOf(view.getText("item")).trim());
            if (m == null || !m.isItem() || m.isAir()) {
                openOptions(p, particle, lm().getString("builder.err_item", p));
                return;
            }
            slot.material = m.name();
        }

        Session s = session(p);
        if (s.slots.size() < MAX_SLOTS) s.slots.add(slot);
        openMain(p);
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

    private List<SingleOptionDialogInput.OptionEntry> paletteEntries(Player p) {
        List<SingleOptionDialogInput.OptionEntry> list = new ArrayList<>();
        list.add(SingleOptionDialogInput.OptionEntry.create(NO_PALETTE, text(p, "builder.palette_none"), true));
        for (String[] c : PALETTE) {
            Component label = Component.text("■■■ ").color(TextColor.fromHexString("#" + c[1]))
                    .append(Component.text("&" + c[0] + "  #" + c[1]).color(TextColor.color(0xAAAAAA)));
            list.add(SingleOptionDialogInput.OptionEntry.create(c[0], label, false));
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
        if (s.slots.isEmpty()) {
            s.error = lm().getString("builder.err_empty", p);
            openMain(p);
            return;
        }

        List<Map<String, Object>> walk = new ArrayList<>();
        List<Map<String, Object>> idle = new ArrayList<>();
        for (Slot slot : s.slots) {
            if (slot.use != Use.IDLE) walk.add(entry(slot, true));
            if (slot.use != Use.WALK) idle.add(entry(slot, false));
        }
        String id = makeId(plain);
        Slot first = s.slots.get(0);
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

    private ItemStack slotIcon(Slot slot) {
        if (slot.color != null) {
            ItemStack potion = new ItemStack(Material.POTION);
            if (potion.getItemMeta() instanceof PotionMeta meta) {
                meta.setColor(slot.color);
                potion.setItemMeta(meta);
            }
            return potion;
        }
        return new ItemStack(ParticleCatalog.icon(slot.particle));
    }

    private Component slotLine(Player p, int index, Slot slot) {
        Component line = Component.text((index + 1) + ". " + ParticleCatalog.label(slot.particle) + " ");
        if (slot.color != null) line = line.append(swatch(slot.color));
        if (slot.color2 != null && !slot.color2.equals(slot.color)) line = line.append(Component.text("→")).append(swatch(slot.color2));
        if (slot.material != null) line = line.append(Component.text(slot.material.toLowerCase(Locale.ROOT) + " "));
        String useKey = switch (slot.use) {
            case WALK -> "builder.use_walk";
            case IDLE -> "builder.use_idle";
            case BOTH -> "builder.use_both";
        };
        return line.append(legacy("§7(" + lm().getString(useKey, p) + "§7)"));
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
