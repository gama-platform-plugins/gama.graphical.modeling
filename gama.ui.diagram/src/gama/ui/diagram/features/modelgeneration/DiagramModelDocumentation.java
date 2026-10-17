package gama.ui.diagram.features.modelgeneration;

import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.services.Graphiti;

import gaml.compiler.ui.wizards.species.ModelDocumentation;

/** Model header metadata stored with the diagram, independently of generated compiler resources. */
public final class DiagramModelDocumentation {
	private static final String PREFIX = "gama.model.documentation.";

	private DiagramModelDocumentation() {}

	public static ModelDocumentation read(final Diagram diagram) {
		return new ModelDocumentation(value(diagram, "author"), value(diagram, "tags"), value(diagram, "text"));
	}

	private static String value(final Diagram diagram, final String key) {
		final String value = Graphiti.getPeService().getPropertyValue(diagram, PREFIX + key);
		return value == null ? "" : value;
	}

	public static void write(final Diagram diagram, final ModelDocumentation documentation) {
		Graphiti.getPeService().setPropertyValue(diagram, PREFIX + "author", documentation.author());
		Graphiti.getPeService().setPropertyValue(diagram, PREFIX + "tags", documentation.tags());
		Graphiti.getPeService().setPropertyValue(diagram, PREFIX + "text", documentation.documentation());
	}
}
