package gama.ui.diagram.editor;

import org.eclipse.gef.palette.PaletteGroup;
import org.eclipse.gef.palette.PaletteRoot;
import org.eclipse.graphiti.ui.editor.DefaultPaletteBehavior;
import org.eclipse.graphiti.ui.editor.DiagramBehavior;

class GamaPaletteBehavior extends DefaultPaletteBehavior {
	GamaPaletteBehavior(final DiagramBehavior behavior) {
		super(behavior);
	}

	@Override
	public PaletteRoot getPaletteRoot() {
		final PaletteRoot root = super.getPaletteRoot();
		configureSelection(root);
		return root;
	}

	@Override
	public void refreshPalette() {
		super.refreshPalette();
		configureSelection(super.getPaletteRoot());
	}

	static void configureSelection(final PaletteRoot root) {
		for (final var entry : root.getChildren()) {
			if (!(entry instanceof PaletteGroup group)) continue;
			group.setLabel("Selection");
			for (final var tool : group.getChildren()) {
				tool.setLabel(tool == root.getDefaultEntry() ? "Single selection" : "Multiple selection");
			}
		}
	}
}
