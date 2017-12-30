package info.gomeow.chester;

import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jibble.jmegahal.JMegaHal;

import info.gomeow.chester.API.ChesterBroadcastEvent;
import info.gomeow.chester.API.AsyncChesterLogEvent;
import to.us.mlgfort.communicationconnector.CommunicationConnector;

public class ChesterCommunicator implements Runnable {
    private static final Random RAND = new Random();
    private final BlockingQueue<String> input = new LinkedBlockingQueue<String>();
    private final JMegaHal brain;
    private final List<String> triggers;
    private final Chester plugin;
    private final Thread thread;
    private final CommunicationConnector communicationConnector;

    public ChesterCommunicator(Chester plugin, JMegaHal brain, List<String> triggers) {
        this.plugin = plugin;
        this.brain = brain;
        this.triggers = triggers;
        this.thread = new Thread(null, this, "Chester");
        this.thread.start();
        communicationConnector = (CommunicationConnector)plugin.getServer().getPluginManager().getPlugin("CommunicationConnector");
    }

    @Override
    public void run() {
        try {
            while (true) {
                String message = input.take();
                for (final String trigger : triggers) {
                    if (message.matches("^.*(?i)" + trigger + ".*$")) {
                        final String sentence = getSentence(trigger, message);

                        new BukkitRunnable() {
                            public void run() {
                                final ChesterBroadcastEvent cbe = new ChesterBroadcastEvent(sentence);
                                plugin.getServer().getPluginManager().callEvent(cbe);
                                String name = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("nickname")) + " ";
                                ChatColor color = ChatColor.getByChar(plugin.getConfig().getString("chatcolor"));
                                String msg = ChatColor.translateAlternateColorCodes('&', sentence);
                                for(Player plyer : cbe.getRecipients()) {
                                    plyer.sendMessage(name + color + msg);
                                }
                                System.out.println(ChatColor.stripColor(msg));
                                communicationConnector.sendToAllApps(name.substring(0, name.length() - 2), msg);
                            }
                        }.runTaskLater(plugin, ThreadLocalRandom.current().nextLong(30L, 80L)); //Delay output from 1.5-4 seconds for "natural" response time
                        break;
                    }
                }
            }
        } catch (InterruptedException ex) {
            // We're done.
        }
    }

    private synchronized String getSentence(String trigger, String message) {
        message = message.replaceAll("(?i)" + trigger, "");
        String[] messageArray = message.split(" ");
        String sentence;

        if (messageArray.length > 1) //RoboMWM - use a word in the message to get from Chester
            sentence = brain.getSentence(messageArray[RAND.nextInt(messageArray.length)]);
        else
            sentence = brain.getSentence();
        for (String triggers : triggers) //RoboMWM - check for all triggers
        {
            //If sentence contains a trigger word, remove it and append another sentence
            while (sentence.matches("^.*(?i)" + trigger + ".*$"))
            {
                sentence = sentence.replaceAll("(?i)" + trigger, "");
                String[] sentenceArray = sentence.split(" ");
                sentence += brain.getSentence(sentenceArray[RAND.nextInt(sentenceArray.length)]);
                //sentence = brain.getSentence(message.replaceAll("(?i)" + trigger, "").split(" ")[RAND.nextInt(message.split(" ").length)]);
            }
        }
        return sentence;
    }

    /**
     * Adds a sentence to the JMegaHal object provided when this
     * ChesterCommunicator was instantiated. Any changes to the JMegaHal
     * object should be handled through this method as it is synchronized
     * with all other access to the JMegaHal object.
     *
     * @param sentence the sentence to add
     */
    public synchronized void addSentenceToBrain(String sentence) {
        brain.add(sentence);
    }

    /**
     * Adds a message to Chester's handling Queue. This method is thread safe
     * and should be called after a {@link AsyncChesterLogEvent} has been fired.
     *
     * @param sentence the sentence to queue
     */
    public void queueMessage(String sentence) {
        input.add(sentence);
    }

    /**
     * Stops the handling of messages by the Consumer task.
     */
    public void stop() {
        this.thread.interrupt();
    }
}
