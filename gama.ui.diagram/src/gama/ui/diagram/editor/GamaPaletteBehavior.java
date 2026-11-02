package gama.ui.diagram.editor;

import org.eclipse.gef.Tool;
import org.eclipse.gef.palette.PaletteDrawer;
import org.eclipse.gef.palette.PaletteGroup;
import org.eclipse.gef.palette.PaletteRoot;
import org.eclipse.gef.palette.ToolEntry;
import org.eclipse.gef.tools.SelectionTool;
import org.eclipse.graphiti.ui.editor.DefaultPaletteBehavior;
import org.eclipse.graphiti.ui.editor.DiagramBehavior;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import gama.ui.diagram.features.modelgeneration.SaveDiagramSnapshotFeature;

class GamaPaletteBehavior extends DefaultPaletteBehavior {
	private PaletteDrawer editingDrawer;

	GamaPaletteBehavior(final DiagramBehavior behavior) {
		super(behavior);
	}

	@Override
	public PaletteRoot getPaletteRoot() {
		final PaletteRoot root = super.getPaletteRoot();
		configureSelection(root);
		configureEditing(root);
		return root;
	}

	@Override
	public void refreshPalette() {
		super.refreshPalette();
		getPaletteRoot();
	}

	private void configureEditing(final PaletteRoot root) {
		if (diagramBehavior == null || !(diagramBehavior.getDiagramContainer() instanceof GamaDiagramEditor editor)
				|| !editor.isLive()) return;
		if (editingDrawer == null) {
			editingDrawer = new PaletteDrawer("Editing");
			editingDrawer.setInitialState(PaletteDrawer.INITIAL_STATE_OPEN);
			final var icon = PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ETOOL_SAVE_EDIT);
			editingDrawer.add(new ToolEntry("Edit snapshot...",
					"Save and open an independent, editable diagram. The GAML source is not changed.", icon, icon) {
				@Override
				public Tool createTool() {
					return new SelectionTool() {
						@Override
						public void activate() {
							super.activate();
							final var domain = getDomain();
							final var shell = editor.getSite().getShell();
							final var control = editor.getGraphicalViewer().getControl();
							shell.getDisplay().asyncExec(() -> {
								if (shell.isDisposed() || control.isDisposed()) return;
								domain.loadDefaultTool();
								SaveDiagramSnapshotFeature.edit(editor);
							});
						}
					};
				}
			});
		}
		if (!root.getChildren().contains(editingDrawer)) root.add(editingDrawer);
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
