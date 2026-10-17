package gama.ui.diagram.editor;

import org.eclipse.gef.ContextMenuProvider;
import org.eclipse.gef.EditPartViewer;
import org.eclipse.gef.ui.actions.ActionRegistry;
import org.eclipse.graphiti.features.context.impl.CustomContext;
import org.eclipse.graphiti.ui.internal.action.CustomAction;
import org.eclipse.graphiti.ui.internal.action.SaveImageAction;
import org.eclipse.graphiti.ui.internal.action.UpdateAction;
import org.eclipse.graphiti.ui.platform.IConfigurationProvider;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.ui.actions.ActionFactory;

import gama.ui.diagram.features.layout.LayoutDiagramFeature;
import gama.ui.diagram.features.modelgeneration.ModelGenerationFeature;

/** A closed menu: unrelated workbench contributions must not extend it. */
final class GamaDiagramContextMenuProvider extends ContextMenuProvider {

	private final ActionRegistry registry;
	private final IConfigurationProvider configuration;

	GamaDiagramContextMenuProvider(final EditPartViewer viewer, final ActionRegistry registry,
			final IConfigurationProvider configuration) {
		super(viewer);
		this.registry = registry;
		this.configuration = configuration;
	}

	@Override
	public void buildContextMenu(final IMenuManager menu) {
		menu.add(registry.getAction(ActionFactory.UNDO.getId()));
		menu.add(registry.getAction(ActionFactory.DELETE.getId()));
		menu.add(new Separator());
		menu.add(registry.getAction(UpdateAction.ACTION_ID));

		final var behavior = configuration.getDiagramBehavior();
		final var context = new CustomContext(behavior.getSelectedPictogramElements());
		final var features = configuration.getDiagramTypeProvider().getFeatureProvider();
		final var layout = new CustomAction(new LayoutDiagramFeature(features), context, behavior);
		layout.setText("Layout");
		menu.add(layout);

		menu.add(new Separator());
		final var exportGaml = new CustomAction(new ModelGenerationFeature(features), context, behavior);
		exportGaml.setText("Export as GAML");
		menu.add(exportGaml);
		final var exportImage = registry.getAction(SaveImageAction.ACTION_ID);
		if (exportImage != null) {
			exportImage.setText("Export...");
			menu.add(exportImage);
		}
	}
}
