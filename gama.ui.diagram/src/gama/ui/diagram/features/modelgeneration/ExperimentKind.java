package gama.ui.diagram.features.modelgeneration;

import gama.ui.diagram.metamodel.EBatchExperiment;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.GamaFactory;

/** Explicit kinds override the legacy GUI/batch drawing subtype without replacing linked objects. */
public final class ExperimentKind {
	private ExperimentKind() {}

	public static String of(final EExperiment experiment) {
		for (final var facet : experiment.getFacets()) {
			if ("type".equals(facet.getName()) && facet.getValue() != null && !facet.getValue().isBlank()) {
				return normalize(facet.getValue());
			}
		}
		return experiment instanceof EBatchExperiment ? "batch" : "gui";
	}

	public static void set(final EExperiment experiment, final String kind) {
		final String normalized = normalize(kind);
		if (!java.util.List.of("gui", "batch", "test").contains(normalized)) {
			throw new IllegalArgumentException("Unknown experiment kind: " + kind);
		}
		var facet = experiment.getFacets().stream().filter(f -> "type".equals(f.getName())).findFirst().orElse(null);
		if (facet == null) {
			facet = GamaFactory.eINSTANCE.createEFacet();
			facet.setName("type");
			experiment.getFacets().add(facet);
		}
		facet.setValue(normalized);
	}

	private static String normalize(final String kind) {
		if (kind == null) return "";
		final String value = kind.trim();
		if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')
				&& value.charAt(value.length() - 1) == value.charAt(0)) {
			return new org.eclipse.xtext.conversion.impl.STRINGValueConverter().toValue(value, null);
		}
		return value;
	}
}
