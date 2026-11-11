package dev.quiqzy.client.module.combat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Hole ESP: zeigt sichere Löcher (rundum Bedrock/Obsidian) – grün = nur Bedrock, gelb = mit Obsidian. */
public final class HoleEspModule extends Module {
	public final Setting.Int range = add(new Setting.Int("range", "Range", 8, 3, 16, " b"));
	public final Setting.Int height = add(new Setting.Int("height", "Height", 10, 0, 100, " %"));
	public final Setting.Color bedrock = add(new Setting.Color("bedrock_color", "Bedrock Colour", 0x3DFF7A));
	public final Setting.Color obsidian = add(new Setting.Color("obsidian_color", "Obsidian Colour", 0xFFC93D));

	public record Hole(BlockPos pos, boolean safe) {
	}

	public volatile List<Hole> holes = List.of();
	private int timer;

	public HoleEspModule() {
		super("hole_esp", "Hole ESP", "Shows safe holes for crystal fights", Category.COMBAT);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.level == null || ++timer < 8) return;
		timer = 0;
		List<Hole> out = new ArrayList<>();
		BlockPos c = mc.player.blockPosition();
		int r = range.get();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) for (int y = -4; y <= 3; y++) for (int z = -r; z <= r; z++) {
			m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
			if (!mc.level.getBlockState(m).isAir() || !mc.level.getBlockState(m.above()).isAir() || !mc.level.getBlockState(m.above(2)).isAir()) continue;
			boolean ok = true, allBedrock = true;
			for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
				var st = mc.level.getBlockState(m.relative(d));
				if (st.is(Blocks.BEDROCK)) continue;
				if (st.is(Blocks.OBSIDIAN) || st.is(Blocks.CRYING_OBSIDIAN) || st.is(Blocks.RESPAWN_ANCHOR) || st.is(Blocks.ENDER_CHEST)) {
					allBedrock = false;
					continue;
				}
				ok = false;
				break;
			}
			if (ok) out.add(new Hole(m.immutable(), allBedrock));
		}
		holes = out;
	}
}
