package gama.ui.diagram.editor;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.xtext.ui.editor.model.IXtextDocument;
import org.eclipse.xtext.ui.editor.model.IXtextModelListener;

import gama.ui.diagram.features.modelgeneration.DescriptionDiagramImporter;
import gama.ui.diagram.metamodel.EGamaModel;
import gaml.compiler.resource.GamlResource;
import gaml.compiler.ui.editor.GamlEditor;
import gaml.compiler.ui.editor.GamlEditorState;
import gaml.compiler.ui.editor.toolbar.OpenExperimentSelectionListener;
import gaml.compiler.ui.wizards.species.ModelDocumentation;
import gama.api.GAMA;

/** Owns the source subscription, not the source document or its compiler resources. */
final class LiveDiagramSession implements AutoCloseable, IPartListener2 {

	private record Snapshot(EGamaModel model, ModelDocumentation documentation, GamlEditorState state) {}

	private final GamaDiagramEditor diagram;
	private final GamlEditor source;
	private final Display display;
	private final AtomicLong revision = new AtomicLong();
	private final IXtextModelListener modelListener = resource -> refresh();
	private final IPropertyListener sourcePropertyListener;
	private final IDocumentListener documentListener = new IDocumentListener() {
		@Override
		public void documentAboutToBeChanged(final DocumentEvent event) {}

		@Override
		public void documentChanged(final DocumentEvent event) { refresh(); }
	};
	private IXtextDocument document;
	private Job job;
	private volatile boolean closed;
	private GamlEditorState state;
	private long appliedRevision = -1;

	LiveDiagramSession(final GamaDiagramEditor diagram, final GamlEditor source) {
		this.diagram = diagram;
		this.source = source;
		sourcePropertyListener = (part, property) -> {
			if (property == IEditorPart.PROP_DIRTY && !closed) diagram.updateDirtyState();
		};
		display = diagram.getSite().getShell().getDisplay();
		source.getSite().getPage().addPartListener(this);
		source.addPropertyListener(sourcePropertyListener);
		bindDocument();
	}

	private void bindDocument() {
		unbindDocument();
		document = source.getDocument();
		if (document != null) {
			document.addDocumentListener(documentListener);
			document.addModelListener(modelListener);
		}
		refresh();
	}

	private void unbindDocument() {
		if (document == null) return;
		document.removeDocumentListener(documentListener);
		document.removeModelListener(modelListener);
		document = null;
	}

	void refresh() {
		final long requestedRevision = revision.incrementAndGet();
		if (closed || display.isDisposed()) return;
		display.asyncExec(() -> {
			if (closed || requestedRevision != revision.get()) return;
			state = null;
			diagram.showLiveStatus("Updating from GAML...", null);
			if (job != null) job.cancel();
			final IXtextDocument requestedDocument = document;
			if (requestedDocument == null) {
				diagram.showLiveStatus("Source document unavailable. Showing the last valid diagram.", null);
				return;
			}
			job = new Job("Updating live GAML diagram") {
				@Override
				protected IStatus run(final IProgressMonitor monitor) {
					try {
						if (monitor.isCanceled()) return Status.CANCEL_STATUS;
						final Snapshot snapshot = requestedDocument.readOnly(resource -> {
							final var gaml = (GamlResource) resource;
							if (gaml.hasErrors()) return null;
							final var description = gaml.buildCompleteDescription();
							if (description == null) return null;
							try {
								if (description.validate() == null || gaml.getValidationContext().hasErrors()) return null;
								return new Snapshot(DescriptionDiagramImporter.importModel(description),
										ModelDocumentation.read(requestedDocument.get()),
										new GamlEditorState(gaml.getValidationContext(), description.getExperiments()));
							} finally {
								description.dispose();
							}
						});
						if (monitor.isCanceled() || closed || display.isDisposed()) return Status.CANCEL_STATUS;
						display.asyncExec(() -> {
							if (closed || requestedRevision != revision.get()) return;
							if (snapshot == null) {
								diagram.showLiveStatus(diagram.getDiagram().getChildren().isEmpty()
										? "GAML has errors. No diagram available yet."
										: "GAML has errors. Showing the last valid diagram.", null);
								return;
							}
							try {
								diagram.applyLiveModel(snapshot.model(), snapshot.documentation());
								state = snapshot.state();
								appliedRevision = requestedRevision;
								diagram.showLiveStatus("Live view - edit the model in the GAML editor.", state);
							} catch (final RuntimeException e) {
								reportFailure(e, requestedRevision);
							}
						});
						return Status.OK_STATUS;
					} catch (final OperationCanceledException e) {
						return Status.CANCEL_STATUS;
					} catch (final RuntimeException e) {
						return reportFailure(e, requestedRevision);
					}
				}
			};
			job.setSystem(true);
			job.schedule(300);
		});
	}

	private IStatus reportFailure(final RuntimeException error, final long requestedRevision) {
		final var status = new Status(IStatus.ERROR, "gama.ui.diagram", "Cannot refresh the live GAML diagram.", error);
		Platform.getLog(LiveDiagramSession.class).log(status);
		if (!closed && !display.isDisposed()) display.asyncExec(() -> {
			if (!closed && requestedRevision == revision.get()) {
				diagram.showLiveStatus("Refresh failed. See the error log; the diagram may be out of date.", null);
			}
		});
		return status;
	}

	void saveSource(final IProgressMonitor monitor) {
		if (!closed) source.doSave(monitor);
	}

	void launch(final String name) {
		if (!closed && appliedRevision == revision.get() && state != null && state.experiments.contains(name)) {
			new OpenExperimentSelectionListener(source, state, GAMA.getGui().getModelsManager()).launch(name);
		}
	}

	@Override
	public void partInputChanged(final IWorkbenchPartReference reference) {
		if (reference.getPart(false) == source) bindDocument();
	}

	@Override
	public void partClosed(final IWorkbenchPartReference reference) {
		if (reference.getPart(false) == source) {
			diagram.getSite().getPage().closeEditor(diagram, false);
		}
	}

	@Override
	public void close() {
		closed = true;
		revision.incrementAndGet();
		if (job != null) job.cancel();
		unbindDocument();
		source.getSite().getPage().removePartListener(this);
		source.removePropertyListener(sourcePropertyListener);
		state = null;
	}

	@Override
	public void partActivated(final IWorkbenchPartReference reference) {}
	@Override
	public void partBroughtToTop(final IWorkbenchPartReference reference) {}
	@Override
	public void partDeactivated(final IWorkbenchPartReference reference) {}
	@Override
	public void partOpened(final IWorkbenchPartReference reference) {}
	@Override
	public void partHidden(final IWorkbenchPartReference reference) {}
	@Override
	public void partVisible(final IWorkbenchPartReference reference) {}
}
