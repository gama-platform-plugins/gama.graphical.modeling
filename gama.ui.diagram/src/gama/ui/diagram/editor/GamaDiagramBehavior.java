package gama.ui.diagram.editor;

import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.ContextMenuProvider;
import org.eclipse.gef.editparts.LayerManager;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.ui.editor.DefaultRefreshBehavior;
import org.eclipse.graphiti.ui.editor.DefaultPaletteBehavior;
import org.eclipse.graphiti.ui.editor.DiagramBehavior;
import org.eclipse.graphiti.ui.editor.IDiagramContainerUI;

import gama.ui.diagram.features.add.RelationStyle;
 
public class GamaDiagramBehavior extends DiagramBehavior {
	public GamaDiagramBehavior(IDiagramContainerUI diagramContainer) {
		super(diagramContainer);	
		initDefaultBehaviors();
	}

	@Override
	protected DefaultPaletteBehavior createPaletteBehaviour() {
		return new GamaPaletteBehavior(this);
	}

	@Override
	protected ContextMenuProvider createContextMenuProvider() {
		return new GamaDiagramContextMenuProvider(getDiagramContainer().getGraphicalViewer(),
				getDiagramContainer().getActionRegistry(), getConfigurationProvider());
	}

	@Override
	protected boolean shouldRegisterContextMenu() {
		return false;
	}

	@Override
	protected DefaultRefreshBehavior createRefreshBehavior() {
		return new DefaultRefreshBehavior(this) {
			@Override
			public void refresh() {
				super.refresh();
				if (org.eclipse.swt.widgets.Display.getCurrent() != null) {
					updateConnectionLayers();
				}
			}

			@Override
			protected void refresh(final PictogramElement element) {
				super.refresh(element);
				updateConnectionLayers();
			}
		};
	}

	void updateConnectionLayers() {
		final var viewer = getDiagramContainer().getGraphicalViewer();
		if (viewer != null && viewer.getRootEditPart() instanceof LayerManager
				&& viewer.getContents() instanceof GraphicalEditPart) {
			RelationStyle.placeConnectionsBehindShapes((LayerManager) viewer.getRootEditPart(),
					((GraphicalEditPart) viewer.getContents()).getFigure());
		}
	}
		
}
