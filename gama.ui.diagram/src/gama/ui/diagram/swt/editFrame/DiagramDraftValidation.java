package gama.ui.diagram.swt.editFrame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.resource.SynchronizedXtextResourceSet;

import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gaml.compiler.indexer.GamlResourceIndexer;
import gaml.compiler.resource.GamlResource;
import gaml.compiler.resource.GamlResourceServices;

/** Full-model validation for detached dialog previews, isolated from the editor's Run toolbar. */
final class DiagramDraftValidation {
	private DiagramDraftValidation() {}

	static String validate(final String source, final URI uri) {
		final var resources = new SynchronizedXtextResourceSet();
		resources.setClasspathURIContext(ModelGenerator.class);
		final var resource = (GamlResource) resources.createResource(uri);
		try (final var stream = new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))) {
			resource.load(stream, null);
			if (!resource.getErrors().isEmpty()) return resource.getErrors().get(0).getMessage();
			resource.getValidationContext().clear();
			final var description = resource.buildCompleteDescription();
			try {
				if (description == null) return "The candidate model could not be described.";
				final var validated = description.validate();
				for (final var error : resource.getValidationContext().getInternalErrors()) {
					if (error.isError()) return error.toString();
				}
				return validated == null ? "The candidate model could not be validated." : null;
			} finally {
				if (description != null) description.dispose();
			}
		} catch (final IOException error) {
			throw new IllegalStateException("Could not load the candidate diagram model", error);
		} finally {
			try {
				GamlResourceServices.discardValidationContext(resource);
				if (!GamlResourceIndexer.directImportsOf(uri).isEmpty()) {
					resource.reparse("model diagram_wizard_disposed");
					GamlResourceIndexer.updateImports(resource);
				}
			} catch (final IOException error) {
				throw new IllegalStateException("Could not release the candidate diagram's imports", error);
			} finally {
				for (final var loaded : List.copyOf(resources.getResources())) {
					if (loaded instanceof GamlResource gaml) GamlResourceServices.discardValidationContext(gaml);
					loaded.unload();
				}
				resources.getResources().clear();
			}
		}
	}
}
