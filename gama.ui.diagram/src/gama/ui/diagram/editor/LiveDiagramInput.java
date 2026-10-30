package gama.ui.diagram.editor;

import java.util.UUID;

import org.eclipse.emf.common.util.URI;
import org.eclipse.graphiti.ui.editor.DiagramEditorInput;
import org.eclipse.ui.IPersistableElement;

import gaml.compiler.ui.editor.GamlEditor;

/** A session-local input, deliberately excluded from workbench restoration. */
public final class LiveDiagramInput extends DiagramEditorInput {

	private final GamlEditor source;

	public LiveDiagramInput(final GamlEditor source) {
		super(URI.createURI("memory:/diagram/" + UUID.randomUUID() + ".gadl#/"),
				"gama.ui.diagram.editor.GamaDiagramTypeProvider");
		this.source = source;
	}

	public GamlEditor getSource() { return source; }

	@Override
	public IPersistableElement getPersistable() { return null; }

	@Override
	public String getName() { return source.getTitle() + " (live diagram)"; }

	@Override
	public String getToolTipText() { return "Live, read-only diagram of " + source.getTitle(); }
}
