package org.evenmorefish.fish.addons.item;


import com.oheers.fish.api.addons.ItemAddon;
import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;

public class HeadDatabaseItemAddon extends ItemAddon {

    private HeadDatabaseAPI api = null;

    @Override
    public String getIdentifier() {
        return "headdb";
    }

    @Override
    public String getPluginName() {
        return "HeadDatabase";
    }

    @Override
    public String getAuthor() {
        return "EvenMoreFish";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public ItemStack getItemStack(String id) {
        if (api == null) {
            return null;
        }

        if (!api.isHead(id)) {
            getLogger().warning(() -> String.format("No such head with the id %s", id));
            return null;
        }

        return api.getItemHead(id);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemsLoad(DatabaseLoadEvent event) {
        getLogger().info("Detected that HeadDatabase has finished loading...");
        this.api = new HeadDatabaseAPI();

        // The core HDB listener reloads once after all item APIs are ready.
    }

}
