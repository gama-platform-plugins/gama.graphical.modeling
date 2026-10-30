package gama.ui.diagram.editor;

import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.ContextMenuProvider;
import org.eclipse.gef.editparts.LayerManager;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.ui.editor.DefaultRefreshBehavior;
import org.eclipse.graphiti.ui.editor.DefaultPaletteBehavior;
import org.eclipse.graphiti.ui.editor.DefaultPersistencyBehavior;
import org.eclipse.graphiti.ui.editor.DiagramBehavior;
import org.eclipse.graphiti.ui.editor.IDiagramContainerUI;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.core.runtime.IProgressMonitor;

import gama.ui.diagram.features.add.RelationStyle;
 
public class GamaDiagramBehavior extends DiagramBehavior {
	public GamaDiagramBehavior(IDiagramContainerUI diagramContainer) {
		super(diagramContainer);	
	}

	@Override
	protected DefaultPersistencyBehavior createPersistencyBehavior() {
		return new DefaultPersistencyBehavior(this) {
			private boolean isLive() {
				return getDiagramContainer().getDiagramEditorInput() instanceof LiveDiagramInput;
			}

			@Override
			public Diagram loadDiagram(final URI uri) {
				if (!isLive()) return super.loadDiagram(uri);
				final var domain = getEditingDomain();
				final var resource = new XMIResourceImpl(uri.trimFragment());
				final Diagram diagram = Graphiti.getPeCreateService().createDiagram("gamaDiagram", "Live diagram", true);
				domain.getCommandStack().execute(new RecordingCommand(domain) {
					@Override
					protected void doExecute() {
						domain.getResourceSet().getResources().add(resource);
						resource.getContents().add(diagram);
					}
				});
				return diagram;
			}

			@Override
			public boolean isDirty() {
				if (getDiagramContainer().getDiagramEditorInput() instanceof LiveDiagramInput input) {
					return input.getSource().isDirty();
				}
				return super.isDirty();
			}

			@Override
			public void saveDiagram(final IProgressMonitor monitor) {
				if (isLive()) {
					((GamaDiagramEditor) getDiagramContainer()).doSave(monitor);
				} else {
					super.saveDiagram(monitor);
				}
			}
		};
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

	void bufferSelection(final PictogramElement[] elements) {
		setPictogramElementsForSelection(elements);
	}
		
}
