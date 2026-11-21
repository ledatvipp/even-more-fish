package com.oheers.fish.rewards;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import vn.ledat.platform.api.LeDatPlatformProvider;
import vn.ledat.platform.api.executor.ManagedExecutorService;
import vn.ledat.platform.api.scheduler.LeDatScheduler;

/** Optional observer of EMF's delivered native catches. Does not select, replace or grant fish. */
public final class FishingAERewards implements Listener {
    private static final NamespacedKey RECEIPT = NamespacedKey.fromString("evenmorefish:ae_book_receipt");
    private static volatile FishingAERewards current;
    private final JavaPlugin plugin;
    private final Class<?> api;
    private final YamlConfiguration config;
    private final Map<String, Double> weights;
    private final List<FishingBookRoll.Enchant> pool;
    private final ManagedExecutorService.ExecutorHandle executor;
    private final LeDatScheduler.ScopedScheduler scheduler;
    private final CompletableFuture<Void> ready;
    private final String jdbc;
    private volatile boolean active = true;

    public static void start(JavaPlugin plugin) {
        stop();
        if (!plugin.isEnabled()) return;
        File file = new File(plugin.getDataFolder(), "ae-fishing-rewards.yml");
        if (!file.isFile()) plugin.saveResource("ae-fishing-rewards.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.getBoolean("enabled", false)) return;
        var provider = Bukkit.getPluginManager().getPlugin("AdvancedEnchantments");
        if (provider == null || !provider.isEnabled() || !Bukkit.getPluginManager().isPluginEnabled("LeDatPlatform")) {
            plugin.getLogger().warning("AE fishing books unavailable: optional provider disabled"); return;
        }
        try {
            var service = new FishingAERewards(plugin, config, provider.getClass().getClassLoader());
            current = service; Bukkit.getPluginManager().registerEvents(service, plugin);
            plugin.getLogger().info("AE fishing books: " + service.pool.size() + " enabled enchanter registrations; manual="
                    + config.getDouble("manual-chance") + "; auto=" + config.getDouble("auto-chance"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            plugin.getLogger().warning("AE fishing books disabled: " + failure.getClass().getSimpleName());
        }
    }

    public static void stop() {
        var old = current; current = null;
        if (old != null) { old.active = false; org.bukkit.event.HandlerList.unregisterAll(old); old.executor.shutdown(); }
    }

    private FishingAERewards(JavaPlugin plugin, YamlConfiguration config, ClassLoader loader) throws ReflectiveOperationException {
        this.plugin = plugin; this.config = config;
        api = Class.forName("net.advancedplugins.ae.api.AEAPI", true, loader);
        for (String key : List.of("manual-chance", "auto-chance")) FishingBookRoll.wins(config.getDouble(key), new java.util.Random(0));
        if (config.getInt("max-level", 2) < 1 || config.getInt("hourly-cap", 6) < 1
                || config.getInt("cooldown-seconds", 60) < 0 || config.getInt("cooldown-seconds", 60) > 3600)
            throw new IllegalArgumentException("Invalid fishing book bounds");
        var section = config.getConfigurationSection("rarity-weights");
        if (section == null) throw new IllegalArgumentException("Missing rarity weights");
        var configured = new java.util.LinkedHashMap<String, Double>();
        for (String group : section.getKeys(false)) {
            double weight = section.getDouble(group);
            if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Invalid rarity weight");
            configured.put(group, weight);
        }
        weights = Map.copyOf(configured);
        @SuppressWarnings("unchecked") var registered = (List<String>) call("getAllEnchantments", new Class<?>[0]);
        var entries = new ArrayList<FishingBookRoll.Enchant>();
        List<String> selected = config.getStringList("catalog");
        if (!selected.isEmpty() && (selected.size() != 53 || !registered.containsAll(selected)))
            throw new IllegalArgumentException("Exact registered SkyRNG catalog required");
        if (selected.isEmpty()) selected = registered;
        for (String id : selected.stream().distinct().sorted().toList()) {
            Object enchant = call("getEnchantmentInstance", new Class<?>[]{String.class}, id);
            if (enchant == null || !Boolean.TRUE.equals(enchant.getClass().getMethod("isEnabled").invoke(enchant))
                    || !Boolean.TRUE.equals(enchant.getClass().getMethod("isAvailableFromEnchanter").invoke(enchant))) continue;
            String group = (String) call("getGroup", new Class<?>[]{String.class}, id);
            int maximum = (Integer) call("getHighestEnchantmentLevel", new Class<?>[]{String.class}, id);
            if (maximum > 0 && weights.getOrDefault(group, 0.0) > 0) entries.add(new FishingBookRoll.Enchant(id, group, maximum));
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("No registered eligible AE books");
        pool = List.copyOf(entries);
        var platform = LeDatPlatformProvider.get(); scheduler = platform.scheduler().scoped(plugin);
        jdbc = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "ae-fishing-rewards.db").getAbsolutePath();
        executor = platform.executors().single(plugin, "ae-fishing-books", 256);
        ready = executor.runAsync("schema", () -> {
            try (Connection c = connection()) { FishingBookLedger.initialize(c); }
            catch (SQLException failure) { throw new java.util.concurrent.CompletionException(failure); }
        });
    }

    private Object call(String name, Class<?>[] types, Object... args) {
        try { return api.getMethod(name, types).invoke(null, args); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("AE API unavailable: " + name, failure); }
    }

    /** Called inside EMF after the pending delivered catch is accepted at MONITOR, not from another listener. */
    public static void accepted(PlayerFishEvent event) {
        var service = current;
        if (service == null || !service.active || event.isCancelled() || event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                || event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
        try {
            UUID owner = event.getPlayer().getUniqueId();
            Object auto = service.plugin.getClass().getMethod("getAutoFishing").invoke(service.plugin);
            boolean automatic = auto != null && Boolean.TRUE.equals(auto.getClass().getMethod("isActive", UUID.class).invoke(auto, owner));
            committed(event.getPlayer(), event.getHook().getUniqueId(), automatic);
        } catch (ReflectiveOperationException | RuntimeException failure) { service.warn("catch observation", failure); }
    }

    /** Provider-owned post-settlement seam. catchId must be the original stable operation ID, never a new retry ID. */
    public static void committed(Player player, UUID catchId, boolean automatic) {
        var service = current;
        if (service == null || !service.active || player == null || catchId == null || player.getGameMode() != GameMode.SURVIVAL) return;
        UUID owner = player.getUniqueId(); String receipt = "catch:" + catchId;
        try {
            var random = FishingBookRoll.random(receipt);
            String enchant = ""; int level = 0;
            if (FishingBookRoll.wins(service.config.getDouble(automatic ? "auto-chance" : "manual-chance"), random)) {
                var chosen = FishingBookRoll.choose(service.pool, service.weights, random);
                enchant = chosen.id(); level = 1 + random.nextInt(Math.min(chosen.maximum(), service.config.getInt("max-level", 2)));
            }
            String selected = enchant; int frozenLevel = level; long now = System.currentTimeMillis();
            service.sql(c -> { try { return FishingBookLedger.admit(c, receipt, owner, automatic, selected, frozenLevel, now,
                    service.config.getInt("cooldown-seconds", 60) * 1000L, service.config.getInt("hourly-cap", 6)); }
                catch (SQLException failure) { throw new java.util.concurrent.CompletionException(failure); } })
                .whenComplete((entry, error) -> {
                    if (error != null) service.warn("admission", error);
                    else service.scheduler.runOnlinePlayer(owner, online -> service.deliverEntries(online, List.of(entry)));
                });
        } catch (RuntimeException failure) { service.warn("committed catch", failure); }
    }

    private Connection connection() throws SQLException {
        Connection c = DriverManager.getConnection(jdbc);
        try (var s = c.createStatement()) { s.execute("PRAGMA busy_timeout=5000"); s.execute("PRAGMA synchronous=FULL"); }
        catch (SQLException failure) { c.close(); throw failure; }
        return c;
    }
    private <T> CompletableFuture<T> sql(Function<Connection, T> work) {
        return ready.thenCompose(ignored -> executor.supplyAsync("receipt", () -> {
            try (Connection c = connection()) { return work.apply(c); }
            catch (SQLException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }));
    }
    private void warn(String stage, Throwable failure) { plugin.getLogger().warning("AE fishing book " + stage + " deferred: " + failure.getClass().getSimpleName()); }
    private void transition(FishingBookLedger.Entry entry, String from, String to) {
        sql(c -> { try { return FishingBookLedger.transition(c, entry, from, to); }
            catch (SQLException failure) { throw new java.util.concurrent.CompletionException(failure); } })
                .exceptionally(error -> { warn("receipt transition", error); return false; });
    }
    private static boolean storageSpace(Player player) {
        return java.util.Arrays.stream(player.getInventory().getStorageContents()).anyMatch(item -> item == null || item.getType().isAir());
    }
    private static boolean hasReceipt(Player player, String receipt) {
        for (ItemStack item : player.getInventory().getStorageContents())
            if (item != null && item.hasItemMeta() && receipt.equals(item.getItemMeta().getPersistentDataContainer().get(RECEIPT, PersistentDataType.STRING))) return true;
        return false;
    }
    private void deliver(Player player, FishingBookLedger.Entry entry) {
        if (!active || entry.book() == null) return;
        if (entry.state().equals("CLAIMING")) {
            if (hasReceipt(player, entry.receipt())) transition(entry, "CLAIMING", "CLAIMED");
            else message(player, "review-required");
            return;
        }
        if (!entry.state().equals("READY") || !storageSpace(player)) return;
        sql(c -> { try { return FishingBookLedger.transition(c, entry, "READY", "CLAIMING"); }
            catch (SQLException failure) { throw new java.util.concurrent.CompletionException(failure); } })
                .thenAccept(claimed -> { if (claimed) scheduler.runOnlinePlayer(entry.owner(), online -> {
                    if (!active) return;
                    if (!storageSpace(online)) { transition(entry, "CLAIMING", "READY"); return; }
                    try {
                        ItemStack book = ItemStack.deserializeBytes(entry.book());
                        book.editMeta(meta -> meta.getPersistentDataContainer().set(RECEIPT, PersistentDataType.STRING, entry.receipt()));
                        if (!online.getInventory().addItem(book).isEmpty()) return;
                        online.saveData(); transition(entry, "CLAIMING", "CLAIMED"); message(online, "received");
                    } catch (RuntimeException failure) { warn("inventory evidence", failure); }
                }); }).exceptionally(error -> { warn("claim", error); return null; });
    }
    private void message(Player player, String key) {
        String text = config.getString("messages." + key, "");
        if (!text.isBlank()) player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(text));
    }
    private byte[] createBook(Player player, FishingBookLedger.Entry entry) {
        ItemStack book = (ItemStack) call("createEnchantmentBook", new Class<?>[]{String.class, int.class, int.class, int.class, Player.class}, entry.enchant(), entry.level(), 100, 0, player);
        if (book == null || book.getAmount() != 1 || !Boolean.TRUE.equals(call("isCustomEnchantBook", new Class<?>[]{ItemStack.class}, book))
                || !entry.enchant().equals(call("getBookEnchantment", new Class<?>[]{ItemStack.class}, book))
                || (Integer) call("getBookEnchantmentLevel", new Class<?>[]{ItemStack.class}, book) != entry.level()
                || (Integer) call("getSuccessOnBook", new Class<?>[]{ItemStack.class}, book) != 100
                || (Integer) call("getFailureOnBook", new Class<?>[]{ItemStack.class}, book) != 0)
            throw new IllegalStateException("AE book identity/rates differ");
        return book.serializeAsBytes();
    }
    private void deliverEntries(Player player, List<FishingBookLedger.Entry> entries) {
        if (!active) return;
        for (var entry : entries) {
            if (entry.state().equals("PLANNED")) {
                try {
                    byte[] book = createBook(player, entry);
                    sql(c -> { try { return FishingBookLedger.materialize(c, entry, book); }
                        catch (SQLException failure) { throw new java.util.concurrent.CompletionException(failure); } })
                            .thenAccept(frozen -> scheduler.runOnlinePlayer(entry.owner(), online -> deliver(online, frozen)))
                            .exceptionally(error -> { warn("book freeze", error); return null; });
                } catch (RuntimeException failure) { warn("original provider selection", failure); }
            } else deliver(player, entry);
        }
    }
    private void recover(UUID owner) {
        if (!active) return;
        sql(c -> { try { return FishingBookLedger.pending(c, owner); }
            catch (SQLException failure) { throw new java.util.concurrent.CompletionException(failure); } })
                .thenAccept(entries -> scheduler.runOnlinePlayer(owner, player -> deliverEntries(player, entries)))
                .exceptionally(error -> { warn("recovery", error); return null; });
    }
    @EventHandler public void joined(PlayerJoinEvent event) { recover(event.getPlayer().getUniqueId()); }
    @EventHandler public void closed(InventoryCloseEvent event) { recover(event.getPlayer().getUniqueId()); }
}
