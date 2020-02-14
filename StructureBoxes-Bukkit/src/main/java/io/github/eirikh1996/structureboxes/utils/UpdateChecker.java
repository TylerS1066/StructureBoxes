package io.github.eirikh1996.structureboxes.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.eirikh1996.structureboxes.StructureBoxes;
import io.github.eirikh1996.structureboxes.localisation.I18nSupport;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import static io.github.eirikh1996.structureboxes.utils.ChatUtils.COMMAND_PREFIX;

public class UpdateChecker extends BukkitRunnable implements Listener {
    private static UpdateChecker instance;
    private int secondCooldown = 0;
    private int delayBeforeCheckingUpdate = 0;

    private UpdateChecker(){}

    @Override
    public void run() {
        secondCooldown++;
        if (secondCooldown < 3600) {
            return;
        }
        StructureBoxes sb = StructureBoxes.getInstance();
        if (delayBeforeCheckingUpdate == 0) {
            sb.getLogger().info(I18nSupport.getInternationalisedString("Update - Checking for updates"));
        } else if (delayBeforeCheckingUpdate >= 5) {
            delayBeforeCheckingUpdate = 0;
            secondCooldown = 0;
            final double newVersion = getNewVersion(getCurrentVersion());
            if (newVersion <= getCurrentVersion()){
                sb.getLogger().info(I18nSupport.getInternationalisedString("Update - Up to date"));
                return;
            }
            sb.getLogger().warning(String.format(I18nSupport.getInternationalisedString("Update - Update available"), newVersion));
            sb.getLogger().warning("https://dev.bukkit.org/projects/structure-boxes/files");
        }
        delayBeforeCheckingUpdate++;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event){
        final Player player = event.getPlayer();
        final double newVersion = getNewVersion(getCurrentVersion());
        if (newVersion <= getCurrentVersion()){
            return;
        }
        if (!player.hasPermission("structureboxes.update")){
            return;
        }
        player.sendMessage(COMMAND_PREFIX + String.format(I18nSupport.getInternationalisedString("Update - Update available"), newVersion));
        player.sendMessage("https://dev.bukkit.org/projects/structure-boxes/files");
    }

    public static synchronized UpdateChecker getInstance() {
        if (instance == null){
            instance = new UpdateChecker();
        }
        return instance;
    }
    private double getCurrentVersion(){
        return Double.parseDouble(StructureBoxes.getInstance().getDescription().getVersion());
    }

    private double getNewVersion(double currentVersion) {
        try {
            URL url = new URL("https://servermods.forgesvc.net/servermods/files?projectids=349569");
            URLConnection conn = url.openConnection();
            conn.setReadTimeout(5000);
            conn.addRequestProperty("User-Agent", "Structure Boxes Update Checker");
            conn.setDoOutput(true);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            final String response = reader.readLine();
            final Gson gson = new Gson();
            final JsonArray jsonArray = gson.fromJson(response, JsonArray.class);
            if (jsonArray.size() == 0) {
                StructureBoxes.getInstance().getLogger().warning("No files found, or Feed URL is bad.");
                return currentVersion;
            }
            JsonObject jsonObj = (JsonObject) jsonArray.get(jsonArray.size() - 1);
            String versionName = jsonObj.get("name").getAsString();
            String newVersion = versionName.substring(versionName.lastIndexOf("v") + 1);
            return Double.parseDouble(newVersion);
        } catch (Exception e) {
            e.printStackTrace();
            return currentVersion;
        }
    }
}
