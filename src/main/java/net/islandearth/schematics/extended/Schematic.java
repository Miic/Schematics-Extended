package net.islandearth.schematics.extended;

import net.islandearth.schematics.extended.block.NBTBlock;
import net.islandearth.schematics.extended.block.NBTChestBlock;
import net.islandearth.schematics.extended.block.NBTSignBlock;
import net.islandearth.schematics.extended.example.BuildTask;
import net.islandearth.schematics.extended.example.SchematicPlugin;
import net.minecraft.server.v1_16_R2.NBTBase;
import net.minecraft.server.v1_16_R2.NBTCompressedStreamTools;
import net.minecraft.server.v1_16_R2.NBTTagCompound;
import net.minecraft.server.v1_16_R2.NBTTagList;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A utility class that previews and pastes schematics block-by-block with asynchronous support.
 * <br></br>
 * @version 2.0.3
 * @author SamB440 - Schematic previews, centering and pasting block-by-block, class itself
 * @author brainsynder - 1.13 Palette Schematic Reader
 * @author Math0424 - Rotation calculations
 * @author Jojodmo - Legacy (< 1.12) Schematic Reader
 */
public class Schematic {

	private SchematicPlugin plugin;
	private File schematic;

	private short width = 0;
	private short height = 0;
	private short length = 0;

	private byte[] blockDatas;

	private LinkedHashMap<Vector, NBTBlock> nbtBlocks = new LinkedHashMap<>();
	private LinkedHashMap<Integer, BlockData> blocks = new LinkedHashMap<>();

	/**
	 * @param plugin your plugin instance
	 * @param schematic file to the schematic
	 */
	public Schematic(SchematicPlugin plugin, File schematic) {
		this.plugin = plugin;
		this.schematic = schematic;
	}

	/**
	 * Pastes a schematic, with a specified time
	 * @param paster player pasting
	 * @param time time in ticks to paste blocks
	 * @return collection of locations where schematic blocks will be pasted, null if schematic locations will replace blocks
	 * @throws SchematicNotLoadedException when schematic has not yet been loaded
	 * @see #loadSchematic()
	 */
	@Nullable
	public Collection<Location> pasteSchematic(Location loc,
											   Player paster,
											   int time,
											   Options... option) throws SchematicNotLoadedException {
		try {

			if (width == 0
					|| height == 0
					|| length == 0
					|| blocks.isEmpty()) {
				throw new SchematicNotLoadedException("Data has not been loaded yet");
			}

			List<Options> options = Arrays.asList(option);
			Data tracker = new Data();

            LinkedHashMap<Integer, Location> indexLocations = new LinkedHashMap<>();
            LinkedHashMap<Integer, Location> delayedIndexLocations = new LinkedHashMap<>();

            LinkedHashMap<Integer, NBTBlock> nbtData = new LinkedHashMap<>();

			BlockFace face = getDirection(paster);

			/*
			 * Loop through all the blocks within schematic size.
			 */
			for (int x = 0; x < width; ++x) {
				for (int y = 0; y < height; ++ y) {
					for (int z = 0; z < length; ++z) {
						int index = y * width * length + z * width + x;
						Vector point = new Vector(x, y, z);
						Location location = null;
						int width2 = width / 2;
						int length2 = length / 2;
						switch (face) {
							case NORTH:
								location = new Location(loc.getWorld(), (x * - 1 + loc.getBlockX()) + width2, y + loc.getBlockY(), (z + loc.getBlockZ()) + length2);
								break;
							case EAST:
								location = new Location(loc.getWorld(), (-z + loc.getBlockX()) - length2, y + loc.getBlockY(), (-x - 1) + (width + loc.getBlockZ()) - width2);
								break;
							case SOUTH:
								location = new Location(loc.getWorld(), (x + loc.getBlockX()) - width2, y + loc.getBlockY(), (z * - 1 + loc.getBlockZ()) - length2);
								break;
							case WEST:
								location = new Location(loc.getWorld(), (z + loc.getBlockX()) + length2, y + loc.getBlockY(), (x + 1) - (width - loc.getBlockZ()) + width2);
								break;
							default:
								break;
						}

						BlockData data = blocks.get((int) blockDatas[index]);

						/*
						 * Ignore blocks that aren't air. Change this if you want the air to destroy blocks too.
						 * Add items to delayedBlocks if you want them placed last, or if they get broken.
						 */
						Material material = data.getMaterial();
						if (material != Material.AIR) {
							if (NBTMaterial.fromBukkit(material) == null || !NBTMaterial.fromBukkit(material).isDelayed()) {
								indexLocations.put(index, location);
							} else {
								delayedIndexLocations.put(index, location);
							}
						}

						if (nbtBlocks.containsKey(point)) {
							nbtData.put(index, nbtBlocks.get(point));
						}
					}
				}
			}

			// Make sure delayed blocks are placed last
			indexLocations.putAll(delayedIndexLocations);
			delayedIndexLocations.clear();

			/*
			 * Verify location of pasting
			 */

			boolean validated = true;

			for (Location validate : indexLocations.values()) {
                boolean isWater = validate.clone().subtract(0, 1, 0).getBlock().getType() == Material.WATER;
                boolean isAir = new Location(validate.getWorld(), validate.getX(), loc.getY() - 1, validate.getZ()).getBlock().getType() == Material.AIR;
                boolean isSolid = validate.getBlock().getType() != Material.AIR;
                boolean isTransparent = options.contains(Options.IGNORE_TRANSPARENT) && validate.getBlock().isPassable() && validate.getBlock().getType() != Material.AIR;

				if (!options.contains(Options.PLACE_ANYWHERE) && (isWater || isAir || isSolid) && !isTransparent) {
                    // Show fake block where block is interfering with schematic
                    paster.sendBlockChange(validate.getBlock().getLocation(), Material.RED_STAINED_GLASS.createBlockData());
                    validated = false;
				} else {
					// Show fake block for air
		            paster.sendBlockChange(validate.getBlock().getLocation(), Material.GREEN_STAINED_GLASS.createBlockData());
				}

				if (!options.contains(Options.PREVIEW)) {
					Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
						if (validate.getBlock().getType() == Material.AIR) paster.sendBlockChange(validate.getBlock().getLocation(), Material.AIR.createBlockData());
					}, 60);
				}
			}

			if (options.contains(Options.PREVIEW)) return indexLocations.values();
			if (!validated) return null;

			if (options.contains(Options.REALISTIC)) {
				//TODO
			}

			// Start pasting each block every tick
			Scheduler scheduler = new Scheduler();

			tracker.trackCurrentBlock = 0;

			List<Material> validData = new ArrayList<>();
			validData.add(Material.LADDER);
			validData.add(Material.TORCH);
			validData.add(Material.CHEST);
			validData.add(Material.BLACK_STAINED_GLASS_PANE);
			validData.add(Material.BLUE_STAINED_GLASS_PANE);
			validData.add(Material.BROWN_STAINED_GLASS_PANE);
			validData.add(Material.CYAN_STAINED_GLASS_PANE);
			validData.add(Material.GLASS_PANE);
			validData.add(Material.WHITE_STAINED_GLASS_PANE);
			validData.add(Material.GREEN_STAINED_GLASS_PANE);
			validData.add(Material.GRAY_STAINED_GLASS_PANE);
			validData.add(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
			validData.add(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
			validData.add(Material.LIME_STAINED_GLASS_PANE);
			validData.add(Material.MAGENTA_STAINED_GLASS_PANE);
			validData.add(Material.ORANGE_STAINED_GLASS_PANE);
			validData.add(Material.PINK_STAINED_GLASS_PANE);
			validData.add(Material.PURPLE_STAINED_GLASS_PANE);
			validData.add(Material.RED_STAINED_GLASS_PANE);
			validData.add(Material.YELLOW_STAINED_GLASS_PANE);
			validData.add(Material.TORCH);
			validData.addAll(ExtraTags.FENCE_GATES.getMaterials());
			validData.addAll(Tag.SIGNS.getValues());
			validData.addAll(Tag.BANNERS.getValues());
			validData.addAll(Tag.STAIRS.getValues());
			validData.addAll(Tag.BEDS.getValues());
			validData.addAll(Tag.DOORS.getValues());

			// List of block faces to update *after* the schematic is done pasting.
			List<Block> toUpdate = new ArrayList<>();
			indexLocations.forEach((index, location) -> {
				Block block = location.getBlock();
				BlockData data = blocks.get((int) blockDatas[index]);
				if (Tag.STAIRS.getValues().contains(data.getMaterial()) || Tag.FENCES.getValues().contains(data.getMaterial())) {
					toUpdate.add(block);
				}
			});

			scheduler.setTask(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
				// Get the block, set the type, data, and then update the state.
                List<Location> locations = new ArrayList<>(indexLocations.values());
				List<Integer> indexes = new ArrayList<>(indexLocations.keySet());

				Block block = locations.get(tracker.trackCurrentBlock).getBlock();
				BlockData data = blocks.get((int) blockDatas[indexes.get(tracker.trackCurrentBlock)]);
				block.setType(data.getMaterial(), false);
				block.setBlockData(data);
				switch (data.getMaterial()) {
					case SPRUCE_SIGN: case DARK_OAK_SIGN: case ACACIA_SIGN: case BIRCH_SIGN: case JUNGLE_SIGN:
					case CRIMSON_SIGN: case WARPED_SIGN: case OAK_SIGN: case SPRUCE_WALL_SIGN: case DARK_OAK_WALL_SIGN:
					case ACACIA_WALL_SIGN: case CRIMSON_WALL_SIGN: case WARPED_WALL_SIGN: case BIRCH_WALL_SIGN:
					case JUNGLE_WALL_SIGN: case OAK_WALL_SIGN: {
						if (nbtData.containsKey(indexes.get(tracker.trackCurrentBlock))) {
							NBTSignBlock lines = (NBTSignBlock) nbtData.get(indexes.get(tracker.trackCurrentBlock));

							org.bukkit.block.Sign sign = (org.bukkit.block.Sign) block.getState();
							try {
								int current = 0;
								for (String line : lines.getLines()) {
									sign.setLine(current, line);
									current++;
								}
							} catch (WrongIdException e) {
								e.printStackTrace();
							}
							sign.update();
						}

						break;
					}

					case CHEST: case TRAPPED_CHEST: {
						if (nbtData.containsKey(indexes.get(tracker.trackCurrentBlock))) {
							NBTChestBlock nbtChestBlock = (NBTChestBlock) nbtData.get(indexes.get(tracker.trackCurrentBlock));
							try {
								Map<Integer, ItemStack> items = nbtChestBlock.getItems();
								org.bukkit.block.Chest chest = (org.bukkit.block.Chest) block.getState();
								for (Integer location : items.keySet()) {
									chest.getBlockInventory().setItem(location, items.get(location));
								}
							} catch (WrongIdException ignored) { }
						}

						break;
					}

					default: {
						break;
					}
				}

				if (validData.contains(data.getMaterial())) {
					Directional facing = (Directional) block.getState().getBlockData();
					switch (face) {
						case NORTH:
							switch (facing.getFacing()) {
								case NORTH:
									facing.setFacing(BlockFace.NORTH);
									break;
								case SOUTH:
									facing.setFacing(BlockFace.SOUTH);
									break;
								case EAST:
									facing.setFacing(BlockFace.WEST);
									break;
								case WEST:
									facing.setFacing(BlockFace.EAST);
									break;
								default:
									break;
							}

							break;
						case EAST:
							switch (facing.getFacing()) {
								case NORTH:
									facing.setFacing(BlockFace.EAST);
									break;
								case SOUTH:
									facing.setFacing(BlockFace.WEST);
									break;
								case EAST:
									facing.setFacing(BlockFace.NORTH);
									break;
								case WEST:
									facing.setFacing(BlockFace.SOUTH);
									break;
								default:
									break;
							}

							break;
						case SOUTH:
							switch (facing.getFacing()) {
								case NORTH:
									facing.setFacing(BlockFace.SOUTH);
									break;
								case SOUTH:
									facing.setFacing(BlockFace.NORTH);
									break;
								case EAST:
									facing.setFacing(BlockFace.EAST);
									break;
								case WEST:
									facing.setFacing(BlockFace.WEST);
									break;
								default:
									break;
							}

							break;
						case WEST:
							switch (facing.getFacing()) {
								case NORTH:
									facing.setFacing(BlockFace.WEST);
									break;
								case SOUTH:
									facing.setFacing(BlockFace.EAST);
									break;
								case EAST:
									facing.setFacing(BlockFace.SOUTH);
									break;
								case WEST:
									facing.setFacing(BlockFace.NORTH);
									break;
								default:
									break;
							}

							break;
						default:
							break;
					} block.setBlockData(facing);
				}

				block.getState().update(true, false);

                // Play block effects. Change to what you want.
				block.getLocation().getWorld().spawnParticle(Particle.CLOUD, block.getLocation(), 6);
				block.getLocation().getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());

				tracker.trackCurrentBlock++;

				if (tracker.trackCurrentBlock >= locations.size() || tracker.trackCurrentBlock >= indexes.size()) {
					scheduler.cancel();
					tracker.trackCurrentBlock = 0;
					toUpdate.forEach(b -> b.getState().update(true, true));
				}
			}, 0, time));
			return indexLocations.values();
		} catch (Exception e) {
			e.printStackTrace();
		} return null;
	}

	/**
	 * Pastes a schematic, with the time defaulting to 1 block per second
	 * @param location location to paste from
	 * @param paster player pasting
	 * @param options options to apply to this paste
	 * @return list of locations where schematic blocks will be pasted, null if schematic locations will replace blocks
	 * @throws SchematicNotLoadedException when schematic has not yet been loaded
	 * @see #loadSchematic()
	 */
	public Collection<Location> pasteSchematic(Location location, Player paster, Options... options) throws SchematicNotLoadedException {
		return pasteSchematic(location, paster, 20, options);
	}

	/**
	 * Creates a constant preview of this schematic for the player
	 * @param player player
	 */
	public void previewSchematic(Player player) {
		plugin.getPlayerManagement().setBuilding(player.getUniqueId(), this);
		new BuildTask(plugin, player).start();
	}

	/**
	 * Loads the schematic file. This should <b>always</b> be used before pasting a schematic.
	 * @return schematic (self)
	 */
	public Schematic loadSchematic() {

		try {

			/*
			 * Read the schematic file. Get the width, height, length, blocks, and block data.
			 */

			FileInputStream fis = new FileInputStream(schematic);
			NBTTagCompound nbt = NBTCompressedStreamTools.a(fis);

			width = nbt.getShort("Width");
			height = nbt.getShort("Height");
			length = nbt.getShort("Length");

			blockDatas = nbt.getByteArray("BlockData");

			NBTTagCompound palette = nbt.getCompound("Palette");
			NBTTagList tiles = (NBTTagList) nbt.get("BlockEntities");

			/*
			 * Load NBT data
			 */
			if (tiles != null) {
				for (NBTBase tile : tiles) {
					if (tile instanceof NBTTagCompound) {
						NBTTagCompound c = (NBTTagCompound) tile;
						if (!c.isEmpty()) {
							NBTMaterial nbtMaterial = NBTMaterial.fromTag(c);
							if (nbtMaterial != null) {
								switch (nbtMaterial) {
									case SIGN: {
										try {
											NBTSignBlock nbtSignBlock = new NBTSignBlock(c);
											List<String> lines = nbtSignBlock.getLines();
											if (!lines.isEmpty()) nbtBlocks.put(nbtSignBlock.getOffset(), nbtSignBlock);
											//tiles.remove(current);
										} catch (WrongIdException ignored) {
											// It wasn't a sign
										}

										break;
									}

									case CHEST:
									case TRAPPED_CHEST: {
										try {
											NBTChestBlock nbtChestBlock = new NBTChestBlock(c);
											if (!nbtChestBlock.getItems().isEmpty())
												nbtBlocks.put(nbtChestBlock.getOffset(), nbtChestBlock);
										} catch (WrongIdException ignored) {
											// It wasn't a chest
										}
										break;
									}

									default: break;
								}
							}
						}
					}
				}
			}

			/*
			 * 	Explanation:
			 *    The "Palette" is setup like this
			 *      "block_data": id (the ID is a Unique ID that WorldEdit gives that
			 *                    corresponds to an index in the BlockDatas Array)
			 *    So I loop through all the Keys in the "Palette" Compound
			 *    and store the custom ID and BlockData in the palette Map
			 */
			palette.getKeys().forEach(rawState -> {
				int id = palette.getInt(rawState);
				BlockData blockData = Bukkit.createBlockData(rawState);
				blocks.put(id, blockData);
			});

			fis.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return this;
	}

	/**
	 * @param player player to get direction they are facing
	 * @return blockface of cardinal direction player is facing
	 */
	private BlockFace getDirection(Player player) {
		float yaw = player.getLocation().getYaw();
		if (yaw < 0) {
			yaw += 360;
		}

		if (yaw >= 315 || yaw < 45) {
			return BlockFace.SOUTH;
		} else if (yaw < 135) {
			return BlockFace.WEST;
		} else if (yaw < 225) {
			return BlockFace.NORTH;
		} else if (yaw < 315) {
			return BlockFace.EAST;
		} return BlockFace.NORTH;
	}

	/**
	 * Hacky method to avoid "final".
	 */
	protected static class Data {
		int trackCurrentBlock;
	}

	/**
	 * An enum of options to apply whilst previewing/pasting a schematic.
	 */
	public enum Options {
		/**
		 * Previews schematic
		 */
		PREVIEW,
		/**
		 * A realistic building method. Builds from the ground up, instead of in the default slices.
		 * <hr></hr>
		 * <b>*WIP, CURRENTLY DOES NOTHING*</b>
		 * @deprecated does nothing
		 */
		@Deprecated
		REALISTIC,
		/**
		 * Bypasses the verification check and allows placing anywhere.
		 */
		PLACE_ANYWHERE,
        /**
         * Ignores transparent blocks in the placement check
         */
        IGNORE_TRANSPARENT
	}
}