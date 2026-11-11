package dev.quiqzy.client.module.combat;

import dev.quiqzy.client.module.Module;

/** Crystal Optimizer: ein geschlagener Kristall verschwindet sofort bei dir – du kannst direkt den nächsten setzen. */
public final class CrystalOptimizerModule extends Module {
	public CrystalOptimizerModule() {
		super("crystal_optimizer", "Crystal Optimizer", "Hit crystals vanish instantly so you can place the next one", Category.COMBAT);
		setEnabled(true);
	}
}
