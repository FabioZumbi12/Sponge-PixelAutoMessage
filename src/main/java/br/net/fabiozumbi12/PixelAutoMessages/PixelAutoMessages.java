package br.net.fabiozumbi12.PixelAutoMessages;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;

import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.Text.Builder;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.serializer.TextSerializers;

import com.google.inject.Inject;

@Plugin(id = "pixelautomessages", 
name = "PixelAutoMessages", 
version = "1.0.0",
authors="FabioZumbi12", 
description="A plugin to send automatic and scheduled messages to chat.")
public class PixelAutoMessages {
	
	@Inject private Logger logger;
	public Logger getLogger(){	
		return logger;
	}
	
	@Inject
	@ConfigDir(sharedRoot = true)
	private Path configDir;

	@Inject
	@DefaultConfig(sharedRoot = false)
	private File defConfig;
	
	@Inject
	@DefaultConfig(sharedRoot = true)
	private ConfigurationLoader<CommentedConfigurationNode> configManager;		
	private CommentedConfigurationNode config;
	
	@Inject private Game game;

	private Task task;
	private int index = 0;	
	
	public Game getGame(){
		return this.game;
	}
	
	@Listener
    public void onServerStart(GameStartedServerEvent event) {		
		//reload
		CommandSpec setline = CommandSpec.builder()
			    .description(Text.of("Use to reload messages from file."))
			    .permission("pam.cmd.reload")
			    .arguments(GenericArguments.string(Text.of("reload")))
			    .executor((src, args) -> { {
						if (args.<String>getOne("reload").get().equals("reload")){
							//reload config
							initConfig();
							//reload messages
							initMessages();
							src.sendMessage(toText("&a[PixelAutoMessages] Messages reloaded!"));
							return CommandResult.success();	
						}
						throw new CommandException(toText("[PixelAutoMessages] Use /pam reload to reload the messages"));
					}			    	
			    })
			    .build();
		Sponge.getCommandManager().register(this, setline, "pam");
		
		//init config
		initConfig();
		//init messages
		initMessages();
		
		//done
		logger.info(toColor("&aPixelAutoMessages enabled!&r"));
	}
	
	@Listener
    public void onReloadPlugins(GameReloadEvent event) {
		//reload config
		initConfig();
		//reload messages
		initMessages();
	}
	
	private void initMessages(){
		logger.info("Reloading tasks...");
		if (task != null){
			task.cancel();
			logger.info("-> Task stoped");
		}
		
		int interval = config.getNode("configs","interval").getInt(60);
		int total = config.getNode("messages").getChildrenMap().keySet().size();
		String prefix = config.getNode("configs","prefix").getString();
		boolean rand = config.getNode("configs","random").getBoolean();
		
		task = game.getScheduler().createTaskBuilder().interval(interval, TimeUnit.SECONDS).execute(t -> {
			String indstr = String.valueOf(index);
			if (rand && total > 0){
				indstr = String.valueOf(new Random().nextInt(total));
			} 
			if (config.getNode("messages",indstr).hasMapChildren()){
				String msg = config.getNode("messages",indstr,"a-message").getString();				
				int players = config.getNode("messages",indstr,"b-players-online").getInt();
				String perm = config.getNode("messages",indstr,"c-permission").getString();
				String hover = config.getNode("messages",indstr,"d-on-hover").getString();
				String cmd = config.getNode("messages",indstr,"f-click-cmd").getString();				
				String url = config.getNode("messages",indstr,"g-click-url").getString();
				String scmd = config.getNode("messages",indstr,"e-suggest-cmd").getString();
				
				if (players != 0 && game.getServer().getOnlinePlayers().size() < players){
					return;
				}
				
				for (Player p:game.getServer().getOnlinePlayers()){
					if (!perm.isEmpty() && !p.hasPermission(perm)){
						continue;
					}
					
					Builder send = Text.builder();
					msg = msg.replace("{player}", p.getName());
					send.append(toText(prefix+msg));
					
					if (!hover.isEmpty()){		
						hover = hover.replace("{player}", p.getName());
						send.onHover(TextActions.showText(toText(hover)));
					}
					if (!cmd.isEmpty()){
						cmd = cmd.replace("{player}", p.getName());
						if (!cmd.startsWith("/")){
							cmd = "/"+cmd;
						}
						send.onClick(TextActions.runCommand(cmd));
					}				
					if (!url.isEmpty()){
						url = url.replace("{player}", p.getName());
						try {
							send.onClick(TextActions.openUrl(new URL(url)));
						} catch (Exception e) {}
					}	
					if (!scmd.isEmpty()){
						scmd = scmd.replace("{player}", p.getName());
						if (!scmd.startsWith("/")){
							scmd = "/"+scmd;
						}
						send.onClick(TextActions.suggestCommand(scmd));
					}
					
					p.sendMessage(send.build());
				}				
			}
			if (index+1 >= total){
				index = 0;
			} else {
				index++;
			}
		}).submit(this);
		
		logger.info("-> Task started");
	}
	
	private void initConfig(){
		logger.info("-> Config module");
		try {
			Files.createDirectories(configDir);
			if (!defConfig.exists()){
				logger.info("Creating config file...");
				defConfig.createNewFile();
			}
			
			configManager = HoconConfigurationLoader.builder().setFile(defConfig).build();	
			config = configManager.load();
						
			config.getNode("configs","prefix").setValue(config.getNode("configs","prefix").getString("&7[&aAutoMessage&7]&r "));
			config.getNode("configs","interval").setValue(config.getNode("configs","interval").getInt(60));
			config.getNode("configs","random").setValue(config.getNode("configs","random").getBoolean(false));
			
			config.getNode("messages").setComment("Set you messages here! Follow the example and add numbers as index for more messages. \n"
					+ "All fields (except permission) accept the player placeholder {player}.\n"
					+ "\n"
					+ "Note: Use the fields 'click-cmd', 'click-url' and 'suggest-cmd' one at time.");
			if (!config.getNode("messages").hasMapChildren()){
				config.getNode("messages","0","a-message").setComment("Any colored message to send to server");
				config.getNode("messages","0","a-message").setValue("&aThis is the default message. &6Change me now {player}!");
				
				config.getNode("messages","0","b-players-online").setComment("Players online needed to show this message. Set 0 always show.");
				config.getNode("messages","0","b-players-online").setValue(0);
				
				config.getNode("messages","0","c-permission").setComment("Set permissions needed to player receive this message. Leave blank to disable.");
				config.getNode("messages","0","c-permission").setValue("");
				
				config.getNode("messages","0","d-on-hover").setComment("Colored hover message.");
				config.getNode("messages","0","d-on-hover").setValue("&7Hi {player}, i am a hover message!");
				
				config.getNode("messages","0","f-click-cmd").setComment("Command to run on click.");
				config.getNode("messages","0","f-click-cmd").setValue("say Commands work {player}!");
				
				config.getNode("messages","0","g-click-url").setComment("Open a url on click.");
				config.getNode("messages","0","g-click-url").setValue("http://google.com");
				
				config.getNode("messages","0","e-suggest-cmd").setComment("Print a command on player chat.");
				config.getNode("messages","0","e-suggest-cmd").setValue("msg {player} private message to me?");
			}
			
			configManager.save(config);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	@Listener
	public void onStopServer(GameStoppingServerEvent e) {
		logger.info(toColor("&cPixelAutoMessages disabled!&r"));
	}
		
	public static Text toText(String str){
    	return TextSerializers.FORMATTING_CODE.deserialize(str);
    }
	
	public static String toColor(String str){
    	return str.replaceAll("(&([a-fk-or0-9]))", "\u00A7$2"); 
    }
}