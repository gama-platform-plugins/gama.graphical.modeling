package gama.ui.diagram.editor;

import org.eclipse.draw2d.geometry.Point;
import org.eclipse.gef.DefaultEditDomain;
import org.eclipse.gef.DragTracker;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.RequestConstants;
import org.eclipse.gef.requests.SelectionRequest;
import org.eclipse.gef.tools.AbstractTool;
import org.eclipse.gef.tools.MarqueeDragTracker;
import org.eclipse.gef.tools.PanningSelectionTool;
import org.eclipse.graphiti.ui.internal.editor.GFMarqueeDragTracker;
import org.eclipse.swt.SWT;

/** Keeps normal selection and dragging on elements, but pans from the background. */
public final class GamaSelectionTool extends PanningSelectionTool {

	@Override
	protected boolean handleButtonDown(final int button) {
		final boolean handled = super.handleButtonDown(button);
		if (button != 1 || !(getCurrentViewer() instanceof GraphicalViewer viewer)) return handled;
		final var target = viewer.findObjectAt(getLocation());
		final var tracker = getDragTracker();
		if (tracker instanceof MarqueeDragTracker || tracker instanceof GFMarqueeDragTracker
				|| getDragTracker() == null && viewer.findHandleAt(getLocation()) == null
						&& (target == null || target == viewer.getRootEditPart() || target == viewer.getContents())) {
			final var editor = (GamaDiagramEditor) ((DefaultEditDomain) getDomain()).getEditorPart();
			setDragTracker(new BackgroundPanTracker(editor,
					tracker instanceof GFMarqueeDragTracker ? viewer.getRootEditPart() : null));
			return true;
		}
		return handled;
	}

	private static final class BackgroundPanTracker extends AbstractTool implements DragTracker {
		private final GamaDiagramEditor editor;
		private final EditPart doubleClickTarget;
		private Point start;

		BackgroundPanTracker(final GamaDiagramEditor editor, final EditPart doubleClickTarget) {
			this.editor = editor;
			this.doubleClickTarget = doubleClickTarget;
		}

		@Override
		protected String getCommandName() { return RequestConstants.REQ_SELECTION; }

		@Override
		protected boolean handleButtonDown(final int button) {
			if (button != 1) return false;
			start = editor.viewLocation();
			setState(STATE_DRAG_IN_PROGRESS);
			return true;
		}

		@Override
		protected boolean handleDrag() {
			if (start != null && movedPastThreshold()) {
				final var delta = getDragMoveDelta();
				editor.panCanvas(start.x - delta.width, start.y - delta.height);
			}
			return true;
		}

		@Override
		protected boolean handleButtonUp(final int button) {
			if (button == 1 && start != null && !movedPastThreshold()
					&& !getCurrentInput().isShiftKeyDown() && !getCurrentInput().isModKeyDown(SWT.MOD1)) {
				getCurrentViewer().deselectAll();
			}
			start = null;
			setState(STATE_INITIAL);
			return true;
		}

		@Override
		protected boolean handleDoubleClick(final int button) {
			if (doubleClickTarget == null) return false;
			final var request = new SelectionRequest();
			request.setLocation(getLocation());
			request.setType(RequestConstants.REQ_OPEN);
			doubleClickTarget.performRequest(request);
			return true;
		}
	}
}
