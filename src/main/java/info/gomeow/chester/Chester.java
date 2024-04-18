package info.gomeow.chester;

import info.gomeow.chester.API.AsyncChesterLogEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jibble.jmegahal.JMegaHal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

public class Chester extends JavaPlugin implements Listener {

    List<String> triggerwords;

    List<String> newSentences = new ArrayList<String>();

    ChesterCommunicator chester;

    private JMegaHal hal;

    public JMegaHal getHal()
    {
        return hal;
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        if(getConfig().getString("chatcolor") == null) {
            getConfig().set("chatcolor", "r");
        }
        if(getConfig().getString("check-update") == null) {
            getConfig().set("check-update", true);
        }
        triggerwords = getConfig().getStringList("triggerwords");
        if(triggerwords.size() == 0) {
            triggerwords.add("chester");
            getLogger().info("No triggerwords found. Using chester as triggerword.");
            getLogger().info("Make sure the config.yml contains the 'triggerwords', and not just a 'triggerword'");
        }
        getLogger().info("Triggerwords: " + triggerwords);
        startChester();
    }

    @Override
    public void onDisable() {
        this.chester.stop();
        this.writeNewSentences();
    }

    public void firstRun(JMegaHal hal, File f) {
        try {
            f.createNewFile();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
        hal.add("Hello World");
        hal.add("Can I have some coffee?");
        hal.add("Can I have some hot chocolate?");
        hal.add("Pirate emos be like: \"Yarr XD\"");
        hal.add("What is this \"unbiblical cord\". And why do we still let infants have them.");
        hal.add("What do you mean women? I just said I got a smoking hot grill");
        hal.add("I was told there'd be a rapture. Where my rapture at?");
        hal.add("Dude I was not shooting the ball in the wrong hoop, I was just playing ad orientem");
        hal.add("To be flattered endlessly is a curse I wouldn't wish on any man");
        hal.add("He told me to love my neigh-bor");
        hal.add("I just deleted a post that was pure cringe. If you saw it you saw it");
        hal.add("Big banana bat bites the bountiful berry and begins to brightly boast of his banana breaking beauty");
        hal.add("His HOLINESS Pope Francis \uD83D\uDCAA\uD83D\uDDFF His troliness Papa Frank\uD83D\uDDE3\uD83D\uDD25\uD83D\uDD25\uD83D\uDD25");
    }

    public JMegaHal transfer(ObjectInputStream in) throws ClassNotFoundException, IOException {
        JMegaHal hal = (JMegaHal) in.readObject();
        if(in != null) {
            in.close();
        }
        return hal;
    }

    public void startChester() {
        try {
            File chesterFile = new File(this.getDataFolder(), "brain.chester");
            File dir = new File("plugins" + File.separator + "Chester");
            if(!dir.exists()) {
                dir.mkdirs();
            }
            File old = new File(this.getDataFolder(), "chester.brain");
            JMegaHal hal;
            if(old.exists()) {
                hal = transfer(new ObjectInputStream(new FileInputStream(old)));
            } else {
                hal = new JMegaHal();
            }
            if(chesterFile.exists()) {
                FileReader fr = new FileReader(chesterFile);
                BufferedReader br = new BufferedReader(fr);
                String line = null;
                while((line = br.readLine()) != null) {
                    hal.add(line);
                }
                br.close();
            } else {
                firstRun(hal, chesterFile);
            }
            this.chester = new ChesterCommunicator(this, hal, triggerwords);
            this.hal = hal;
        } catch(IOException ioe) {
        } catch(ClassNotFoundException cnfe) {
        }
    }

    public String clean(String string) {
        if(string != null && string.length() > 300) {
            string = string.substring(0, 300);
        }
        String newstring = string.replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", "");
        return newstring;
    }

    public void writeNewSentences() {
        File chesterFile = new File(this.getDataFolder(), "brain.chester");
        try {
            FileWriter fw = new FileWriter(chesterFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            for (String sentence : this.newSentences) {
                bw.write(sentence + "\n");
            }
            bw.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
        }

    }

    public void addToFile(String sentence) {
        this.newSentences.add(sentence);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onChat(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        //If alone, don't log
        boolean isPlayerChattingToSelf = false;
        if (event.getRecipients().size() <= 1)
            isPlayerChattingToSelf = true;
        final boolean doNotLog = isPlayerChattingToSelf;

        final AsyncChesterLogEvent cle = new AsyncChesterLogEvent(player, event.getMessage());
        if (getServer().isPrimaryThread()) //player#chat calls in sync for some reason
        {
            new BukkitRunnable()
            {
                @Override
                public void run()
                {
                    getServer().getPluginManager().callEvent(cle);
                }
            }.runTaskAsynchronously(this);
        }
        else
            getServer().getPluginManager().callEvent(cle);
        final String message = cle.getMessage();
        // Permissions checks aren't thread safe so we need to handle this on the main thread
        new BukkitRunnable() {
            public void run() {
                if(player.hasPermission("chester.log") && !cle.isCancelled() && !doNotLog) {
                    addToFile(clean(message));
                }
                if (player.hasPermission("chester.trigger")) {
                    boolean cancel = false;
                    for(String trigger:triggerwords) {
                        if(message.matches("^.*(?i)" + trigger + ".*$")) {
                            cancel = true;
                            break;
                        }
                    }
                    if(!cancel && !doNotLog) {
                        Chester.this.chester.addSentenceToBrain(message);
                    }
                }
            }
        }.runTask(this);
        this.chester.queueMessage(message);
    }

//    @EventHandler(priority = EventPriority.HIGHEST)
//    void onPlayerJoin(final PlayerJoinEvent event)
//    {
//        new BukkitRunnable()
//        {
//            Player player = event.getPlayer();
//            public void run()
//            {
//                if (!player.isOnline())
//                    return;
//                if (player.hasPlayedBefore())
//                    chester.queueMessage(player.getName() + "U_W0T_B0T hello hi hoi hai m8");
//                else if (!player.hasPlayedBefore())
//                    chester.queueMessage("U_W0T_B0T welcome welcome welcome welcome welcome welcome");
//            }
//        }.runTaskLater(this, 200L);
//    }
}
