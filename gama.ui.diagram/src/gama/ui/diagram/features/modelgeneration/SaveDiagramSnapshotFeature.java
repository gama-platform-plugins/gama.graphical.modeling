package gama.ui.diagram.features.modelgeneration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICustomContext;
import org.eclipse.graphiti.features.custom.AbstractCustomFeature;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.SaveAsDialog;
import org.eclipse.ui.ide.IDE;

import gama.ui.diagram.editor.GamaDiagramEditor;
import gama.ui.diagram.editor.LiveDiagramInput;
import gama.ui.diagram.features.ExampleUtil;

/** Saves a detached copy; the live input and its source subscription are never retargeted. */
public final class SaveDiagramSnapshotFeature extends AbstractCustomFeature {

	public SaveDiagramSnapshotFeature(final IFeatureProvider provider) { super(provider); }

	@Override
	public String getName() { return "Save snapshot..."; }

	@Override
	public String getDescription() { return "Save an independent, editable .gadl diagram"; }

	@Override
	public boolean canExecute(final ICustomContext context) {
		return getDiagram() != null && !getDiagram().getChildren().isEmpty();
	}

	@Override
	public void execute(final ICustomContext context) {
		save((GamaDiagramEditor) ExampleUtil.getDiagramEditor(getFeatureProvider()));
	}

	public static void save(final GamaDiagramEditor editor) {
		save(editor, false);
	}

	public static void edit(final GamaDiagramEditor editor) {
		if (MessageDialog.openConfirm(editor.getSite().getShell(), "Edit independent diagram",
				"This live diagram follows the GAML source and cannot be edited directly.\n\n"
				+ "Save an independent .gadl copy and open it for editing? "
				+ "Changes to that copy will not update the GAML source. The live view will remain synchronized.")) {
			save(editor, true);
		}
	}

	private static void save(final GamaDiagramEditor editor, final boolean openForEditing) {
		if (editor.getDiagram().getChildren().isEmpty()) {
			MessageDialog.openInformation(editor.getSite().getShell(), "No diagram snapshot",
					"Wait for a valid GAML model before saving a diagram snapshot.");
			return;
		}
		final var dialog = new SaveAsDialog(editor.getSite().getShell());
		dialog.setTitle("Save independent diagram snapshot");
		if (editor.getEditorInput() instanceof LiveDiagramInput input && input.getSource().getResource() instanceof IFile source) {
			dialog.setOriginalFile(source.getParent().getFile(
					new Path(source.getName()).removeFileExtension().addFileExtension("gadl")));
		} else {
			dialog.setOriginalName(editor.getDiagram().getName() + ".gadl");
		}
		if (dialog.open() != Window.OK || dialog.getResult() == null) return;
		final IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(dialog.getResult());
		if (!"gadl".equals(file.getFileExtension())) {
			MessageDialog.openError(editor.getSite().getShell(), "Invalid snapshot name",
					"Choose a file name ending in .gadl. The GAML source must not be overwritten.");
			return;
		}
		try {
			final var copy = new XMIResourceImpl(URI.createPlatformResourceURI(file.getFullPath().toString(), true));
			copy.getContents().addAll(EcoreUtil.copyAll(editor.getDiagram().eResource().getContents()));
			final var bytes = new ByteArrayOutputStream();
			copy.save(bytes, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
			try (final var contents = new ByteArrayInputStream(bytes.toByteArray())) {
				if (file.exists()) {
					file.setContents(contents, IResource.KEEP_HISTORY, null);
				} else {
					file.create(contents, false, null);
				}
			}
			editor.getEditorSite().getActionBars().getStatusLineManager().setMessage(
					"Saved independent diagram snapshot: " + file.getFullPath());
		} catch (final IOException | CoreException e) {
			final var status = new Status(IStatus.ERROR, "gama.ui.diagram", "Cannot save the diagram snapshot.", e);
			Platform.getLog(SaveDiagramSnapshotFeature.class).log(status);
			ErrorDialog.openError(editor.getSite().getShell(), "Cannot save snapshot", null, status);
			return;
		}
		if (openForEditing) {
			try {
				IDE.openEditor(editor.getSite().getPage(), file, "gama.ui.diagram.editor", true);
			} catch (final PartInitException e) {
				final var status = new Status(IStatus.ERROR, "gama.ui.diagram",
						"The snapshot was saved, but could not be opened for editing: " + file.getFullPath(), e);
				Platform.getLog(SaveDiagramSnapshotFeature.class).log(status);
				ErrorDialog.openError(editor.getSite().getShell(), "Cannot open snapshot", null, status);
			}
		}
	}
}
