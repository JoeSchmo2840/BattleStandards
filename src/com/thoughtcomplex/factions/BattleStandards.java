package com.thoughtcomplex.factions;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.P;
import com.massivecraft.factions.struct.Rel;
import com.thoughtcomplex.factions.Challenge;


public class BattleStandards extends JavaPlugin implements Listener {
	
	private HashMap<Faction, Challenge> factionChallenges = new HashMap<Faction, Challenge>();
	private ArrayList<Challenge> deadChallenges = new ArrayList<Challenge>();
	private int challengeTask = -1;
	public static double CHALLENGE_MULTIPLIER = 2.0d;
	
	@Override
	public void onEnable() {
		if (getFactions()!=null) {
			getLogger().info("Connected to Factions API v"+getFactions().hookSupportVersion());
		} else {
			getLogger().warning("Cannot connect to Factions API! BattleStandards will disable.");
			getPluginLoader().disablePlugin(this);
		}
		
		challengeTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this,new Runnable(){
			public void run() {
				updateChallenges();
			}
		},0,20);
		Bukkit.getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable() {
		//stop the thread
		if (challengeTask!=-1) Bukkit.getScheduler().cancelTask(challengeTask);
		//cancel all the challenges
		for(Challenge c : factionChallenges.values()) {
			c.kill();
		}
		factionChallenges.clear();
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
		if (command.getLabel().equalsIgnoreCase("challenge")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage(ChatColor.RED+"Console cannot be in factions or place battle standards.");
				return true;
			}
			Player player = (Player)sender;
			FPlayer fplayer = FPlayers.i.get(player);
			if (fplayer.getFaction().isNone()) {
				player.sendMessage(ChatColor.RED+"You need to be a moderator or admin of a faction in order to issue challenges!");
				return true;
			}
			boolean allowed = false;
			if (fplayer.getRole()==Rel.LEADER | fplayer.getRole()==Rel.OFFICER) allowed=true;
			
			if (!allowed) {
				player.sendMessage(ChatColor.RED+"You need to be a moderator or admin of a faction in order to issue challenges!");
				return true;
			}
			
			if (factionChallenges.containsKey(fplayer.getFaction())) {
				Challenge existing = factionChallenges.get(fplayer.getFaction());
				if (existing.challenger.equalsIgnoreCase(player.getName())) {
					player.sendMessage(ChatColor.RED+"You've already got a challenge in progress!");
				} else {
					player.sendMessage(ChatColor.RED+"You can't issue a challenge right now because "+existing.challenger+" is already using the battle standard.");
				}
				return true;
			}
			
			FLocation origin = new FLocation(player);
			Faction territory = Board.getFactionAt(origin);
			
			if (!territory.isNone()) {
				player.sendMessage(ChatColor.RED+"Cannot issue challenges from "+territory.getTag()+" territory! (Must be Wilderness)");
				return true;
			}
			
			ArrayList<Faction> neighbors = new ArrayList<Faction>();
			for(FLocation neighborLocation : getCardinalNeighbors(origin)) {
				Faction f = Board.getFactionAt(neighborLocation);
				if (f.isNone()) continue;
				if (f.equals(fplayer.getFaction())) continue;
				neighbors.add(f);
			}
			
			if (neighbors.isEmpty()) {
				player.sendMessage(ChatColor.RED+"There are no factions here to challenge!");
				
				return true;
			}
			
			String factionListString = "";
			for(int i=0; i<neighbors.size(); i++) {
				factionListString+=neighbors.get(i).getTag();
				if (i!=neighbors.size()-1) factionListString+=", ";
			}
			Challenge challenge = new Challenge(
					player.getName(), fplayer.getFaction(), origin.getWorld(), origin.getX(),origin.getZ(),neighbors
					);
			if (challenge.challengedPlayers>0) {
				if (challenge.isDead()) {
					player.sendMessage(ChatColor.RED+"There are not enough players online in the neighboring factions!");
					return true;
				}
				factionChallenges.put(fplayer.getFaction(), challenge);
				Bukkit.broadcastMessage(fplayer.getNameAndTag()+ChatColor.GOLD+" has planted a Battle Standard and challenged the following factions: "+factionListString);
			} else {
				player.sendMessage(ChatColor.RED+"There are no players online in one or more of the neighboring factions!");
				challenge.kill();
			}
			//System.out.println("You have placed "+fplayer.getFactionId()+"'s battle standard");
			return true;
		}
		
		return false;
	}
	
	@EventHandler(priority=EventPriority.NORMAL)
	public void onPlayerDamage(EntityDamageEvent e) {
		if (!(e.getEntity() instanceof Player)) return;
		if (e.getCause()==DamageCause.PROJECTILE | e.getCause()==DamageCause.ENTITY_ATTACK) {
			FPlayer damaged = FPlayers.i.get((Player)e.getEntity());
			for(Challenge c : factionChallenges.values()) c.tapPlayer(damaged);
		}
	}
	
	@EventHandler(priority=EventPriority.NORMAL)
	public void onPlayerDie(PlayerDeathEvent e) {
		if (e.getEntity()==null) return;
		Faction victimFaction = FPlayers.i.get(e.getEntity()).getFaction();
		if (victimFaction==null) return;
		Challenge affectedChallenge = factionChallenges.get(victimFaction);
		if (affectedChallenge==null) return;
		if (affectedChallenge.challenger.equalsIgnoreCase(e.getEntity().getName())) {
			if (e.getEntity().getKiller()!=null)
				Bukkit.broadcastMessage(ChatColor.GOLD+e.getEntity().getKiller().getDisplayName()+" has killed the standard bearer for "+victimFaction.getTag());
			else Bukkit.broadcastMessage(ChatColor.GOLD+affectedChallenge.challenger+" has foolishly gotten themselves killed in the middle of a challenge.");
			affectedChallenge.kill();
			for(int i=0; i<10; i++)
				FPlayers.i.get(Bukkit.getPlayer(affectedChallenge.challenger)).onDeath();
		}
	}
	
	@EventHandler(priority=EventPriority.NORMAL)
	public void onBlockBreak(BlockBreakEvent e) {
		if (e.getBlock()==null) return;
		Chunk chunk = e.getBlock().getChunk();
		
		for(Challenge c : factionChallenges.values()) {
			if (c.x==chunk.getX() & c.z==chunk.getZ()) {
				//this is a block in the chunk this Challenge was issued in
				if (c.pole.contains(e.getBlock()) | c.flag.contains(e.getBlock())) {
					c.kill();
					Bukkit.broadcastMessage(e.getPlayer().getDisplayName()+ChatColor.GOLD+" has broken "+c.owner.getTag()+"'s battle standard!");
			
				}
			}
		}
	}
	
	public void updateChallenges() {
		for(Challenge challenge : factionChallenges.values()) {
			//if the challengers leave, get rid of the challenge.
			Player challenger = Bukkit.getPlayer(challenge.challenger);
			if (challenger==null) challenge.kill();
			else if (!challenger.isOnline()) challenge.kill();
			if (challenge.owner.getOnlinePlayers().isEmpty()) challenge.kill();
			
			if (challenger!=null) {
				//if the challenger/standard bearer wanders too far from the standard, get rid of the challenge.
				int xdist = (int)Math.abs(challenger.getLocation().getChunk().getX()-challenge.x);
				int zdist = (int)Math.abs(challenger.getLocation().getChunk().getZ()-challenge.z);
				if (xdist>2 | zdist>2) {
					challenger.sendMessage(ChatColor.RED+"You have left the challenge area!");
					challenge.kill();
				}
			}
			
			if (challenge.isDead()) {
				deadChallenges.add(challenge);
				Bukkit.broadcastMessage(ChatColor.GOLD+challenge.challenger+"'s challenge has ended.");
			} else {
				if (challenger!=null) {
					challenger.getWorld().playEffect(challenger.getEyeLocation(), Effect.MOBSPAWNER_FLAMES, 0);
				}
				
				challenge.tick();
				
			}
		}
		
		for(Challenge c : deadChallenges) { factionChallenges.remove(c.owner); }
		deadChallenges.clear();
	}
	
	ArrayList<FLocation> getCardinalNeighbors(FLocation origin) {
		ArrayList<FLocation> result = new ArrayList<FLocation>();
		result.add(origin.getRelative(0,-1));
		result.add(origin.getRelative(0, 1));
		result.add(origin.getRelative(-1,0));
		result.add(origin.getRelative(1, 0));
		return result;
	}
	
	
	public static P getFactions() {
		try {
			Class.forName("com.massivecraft.factions.P");
			P p = P.p;
			return p;
		} catch (ClassNotFoundException e) {
			return null;
		}
	}
	
	
}
