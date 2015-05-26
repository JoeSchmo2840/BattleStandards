package com.thoughtcomplex.factions;

import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;

public class Challenge {
	public static final long SECONDS_PER_POWER_DRAIN = 20;
	
	public String challenger = "";
	public Faction owner = null;
	public long x = 0;
	public long z = 0;
	public World world;
	
	public ArrayList<Faction> challengedFactions = new ArrayList<Faction>();
	public long lastTick = -1;
	private boolean dead = false;
	private Block originBlock = null;
	public ArrayList<Block> pole = new ArrayList<Block>();
	public ArrayList<Block> flag = new ArrayList<Block>();
	
	public int challengedPlayers = -1;
	
	public Challenge(String challenger, Faction owner, World world, long x, long z, Collection<Faction> affectedFactions) {
		challengedFactions.addAll(affectedFactions);
		lastTick = System.nanoTime()/1000000L;
		this.challenger = challenger;
		this.owner = owner;
		this.world = world;
		this.x = x;
		this.z = z;
		int minY = 64;
		
		Block origin = world.getChunkAt((int)x,(int)z).getBlock((int)(Math.random()*16), 64, (int)(Math.random()*16));
		originBlock = world.getHighestBlockAt(origin.getX(), origin.getZ());
		/*
		for(int xi=0; xi<16; xi++) {
			for(int zi=0; zi<16; zi++) {
				int highest = world.getHighestBlockYAt(world.getChunkAt((int)x,(int)z).getBlock(xi, 0, zi).getLocation());
				minY = Math.max(minY, highest);
			}
		}
		originBlock = world.getChunkAt((int)x,(int)z).getBlock(7, minY, 7);
		//originBlock = world.getHighestBlockAt(midpoint.getX(),midpoint.getZ());
		*/
		//challengedPlayers is the SMALLEST online-player-count among all of the challenged factions.
		for(Faction f : affectedFactions) {
			int playersOnline = f.getOnlinePlayers().size();
			if (challengedPlayers==-1) challengedPlayers = playersOnline;
			else {
				if (playersOnline < challengedPlayers) challengedPlayers = playersOnline;
			}
		}
		//System.out.println("Challenge size: "+challengedPlayers);
		
		boolean sufficientChallengeSize = true;
		if (challengedPlayers<=0) sufficientChallengeSize=false;
		if (challengedPlayers*BattleStandards.CHALLENGE_MULTIPLIER < owner.getOnlinePlayers().size()) sufficientChallengeSize=false;
		
		if (challengedPlayers>0) {
			draw();
			Faction warzone = Factions.i.getByTag("WarZone");
			if (warzone!=null) Board.setFactionAt(warzone, new FLocation(originBlock));
		}
	}
	
	private void draw() {
		Block curBlock = originBlock;
		for(int i=0; i<4; i++) {
			if (curBlock.isEmpty()) {
				curBlock.setTypeIdAndData(113, (byte)1, false);
				pole.add(curBlock);
			}
			curBlock = curBlock.getRelative(BlockFace.UP);
		}
		//get a random direction
		BlockFace randomDir = BlockFace.EAST;
		switch( (int)(Math.random()*4) ) {
		case 0: break;
		case 1: randomDir = BlockFace.WEST; break;
		case 2: randomDir = BlockFace.NORTH; break;
		case 3: randomDir = BlockFace.SOUTH; break;
		}
		
		int randomColor = (int)(Math.random()*16);
		//curBlock = curBlock.getRelative(BlockFace.DOWN);
		pole.add(curBlock);
		curBlock.setTypeIdAndData(113,(byte)1,false);
		
		for(int i=0; i<3; i++) {
			curBlock = curBlock.getRelative(randomDir);
			if (curBlock.isEmpty()) {
				flag.add(curBlock);
				curBlock.setTypeIdAndData(35,(byte)randomColor,false);
			}
		}
	}
	
	public void erase() {
		for(Block b : flag) { b.setTypeId(0); }
		for(Block b : pole) { b.setTypeId(0); }
		flag.clear();
		pole.clear();
	}
	
	public void tick() {
		long curTick = System.nanoTime()/1000000L;
		if (curTick-lastTick > 1000*SECONDS_PER_POWER_DRAIN) {
			lastTick = curTick;
			for(Faction f : challengedFactions) {
				Bukkit.broadcastMessage(f.getTag()+" experienced a power drain from "+challenger+"'s challenge!");
				if (f.getPowerMax()>0) f.setPowerBoost(f.getPowerBoost()-challengedPlayers);
			}
		}
		
	}
	
	public void kill() {
		dead=true;
		for(Faction f : challengedFactions) {
			f.setPowerBoost(0);
		}
		/*
		Block midpoint = world.getChunkAt((int)x,(int)z).getBlock(7, 64, 7);
		Block highest = world.getHighestBlockAt(midpoint.getX(),midpoint.getZ());
		Block higher = highest.getRelative(BlockFace.UP);
		*/
		Board.setFactionAt(Factions.i.getNone(), new FLocation(originBlock));
		erase();
	}
	
	public boolean isDead() { return dead; }
	
	public void tapPlayer(FPlayer p) {
		Faction affected = p.getFaction();
		if (challengedFactions.contains(affected) | owner.equals(affected)) {
			//because Shit Got Real, give the challenge a bunch of extra time before we issue another power drain.
			lastTick = (System.nanoTime()/1000000L) + (1000*SECONDS_PER_POWER_DRAIN);
		}
	}
	

}
