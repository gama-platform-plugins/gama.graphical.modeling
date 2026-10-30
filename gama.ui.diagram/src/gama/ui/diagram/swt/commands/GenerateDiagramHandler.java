package gama.ui.diagram.swt.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.ui.PartInitException;

import gama.ui.diagram.editor.GamaDiagramEditor;
import gama.ui.diagram.editor.LiveDiagramInput;
import gama.ui.shared.utils.WorkbenchHelper;
import gaml.compiler.ui.editor.GamlEditor;
import gaml.compiler.ui.editor.IDiagramOpener;

public class GenerateDiagramHandler extends AbstractHandler implements IDiagramOpener {

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		if (WorkbenchHelper.getActiveEditor() instanceof GamlEditor editor) open(editor);
		return null;
	}

	@Override
	public void open(final GamlEditor editor) {
		final var page = editor.getSite().getPage();
		for (final var reference : page.getEditorReferences()) {
			if (reference.getEditor(false) instanceof GamaDiagramEditor diagram
					&& diagram.getEditorInput() instanceof LiveDiagramInput input && input.getSource() == editor) {
				page.activate(diagram);
				return;
			}
		}
		try {
			page.openEditor(new LiveDiagramInput(editor), "gama.ui.diagram.editor");
		} catch (final PartInitException e) {
			final var status = new Status(IStatus.ERROR, "gama.ui.diagram", "Cannot open the live diagram.", e);
			Platform.getLog(GenerateDiagramHandler.class).log(status);
			ErrorDialog.openError(editor.getSite().getShell(), "Cannot open diagram", null, status);
		}
	}

	@Override
	public void close(final GamlEditor editor) {
		final var page = editor.getSite().getPage();
		for (final var reference : page.getEditorReferences()) {
			if (reference.getEditor(false) instanceof GamaDiagramEditor diagram
					&& diagram.getEditorInput() instanceof LiveDiagramInput input && input.getSource() == editor) {
				page.closeEditor(diagram, false);
			}
		}
	}
}
