package gama.ui.diagram.features.modelgeneration;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.URI;

import gama.api.compilation.descriptions.IDescription;
import gama.api.compilation.descriptions.IExperimentDescription;
import gama.api.compilation.descriptions.IModelDescription;
import gaml.compiler.resource.GamlResource;
import gaml.compiler.resource.GamlResourceServices;
import gaml.compiler.ui.editor.GamlEditorState;

/** Checks URI-scoped compiler notifications without requiring the Eclipse workbench. */
public final class ValidationIsolationTest {

	private static IModelDescription model(final String... names) {
		return model(new boolean[1], names);
	}

	private static IModelDescription model(final boolean[] disposed, final String... names) {
		final List<IExperimentDescription> experiments = new ArrayList<>();
		for (final String name : names) {
			experiments.add((IExperimentDescription) Proxy.newProxyInstance(
					ValidationIsolationTest.class.getClassLoader(), new Class<?>[] { IExperimentDescription.class },
					(proxy, method, args) -> switch (method.getName()) {
						case "getName" -> name;
						case "isAbstract" -> name.startsWith("abstract:");
						case "getLitteral" -> "gui";
						case "isMemorize" -> false;
						default -> throw new AssertionError("Unexpected experiment call: " + method.getName());
					}));
		}
		return (IModelDescription) Proxy.newProxyInstance(ValidationIsolationTest.class.getClassLoader(),
				new Class<?>[] { IModelDescription.class }, (proxy, method, args) -> {
					return switch (method.getName()) {
						case "getExperiments" -> {
							check(!disposed[0], "Read a disposed description");
							yield experiments;
						}
						case "validate" -> proxy;
						case "dispose" -> {
							disposed[0] = true;
							experiments.clear();
							yield null;
						}
						default -> throw new AssertionError("Unexpected model call: " + method.getName());
					};
				});
	}

	private static GamlResource resource(final URI uri) {
		return resource(uri, null);
	}

	private static GamlResource resource(final URI uri, final IModelDescription description) {
		final GamlResource resource = new GamlResource() {
			@Override
			public boolean hasErrors() { return false; }

			@Override
			public IModelDescription buildCompleteDescription() { return description; }
		};
		resource.setURI(uri);
		return resource;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		final URI diagram = URI.createURI("platform:/resource/Life/diagrams/life_model.gadl#/");
		final URI fullModelURI = diagram.trimFragment().trimFileExtension().appendFileExtension("gaml");
		final URI snippetURI = ModelGenerator.syntaxCheckURI(diagram);
		check(!snippetURI.equals(fullModelURI) && snippetURI.fragment() == null, "Syntax checks share the model URI");
		check(snippetURI.trimSegments(1).equals(fullModelURI.trimSegments(1)), "Relative resource base changed");
		check("gaml".equals(snippetURI.fileExtension()), "Snippet is not a GAML resource");
		final GamlResource fullModel = resource(fullModelURI);
		final GamlResource snippet = resource(snippetURI);
		final List<String> toolbarExperiments = new ArrayList<>();
		final int[] callbacks = { 0 };
		GamlResourceServices.addResourceListener(fullModelURI, (model, experiments, status) -> {
			callbacks[0]++;
			toolbarExperiments.clear();
			for (final IDescription experiment : experiments) { toolbarExperiments.add(experiment.getName()); }
		});
		try {
			fullModel.updateWith(model("Game of Life"), true);
			check(toolbarExperiments.equals(List.of("Game of Life")), "Full-model experiment not reported");

			// Reproduce the original collision: a dummy model under the same URI clears the Run list.
			resource(fullModelURI).updateWith(model(), true);
			check(toolbarExperiments.isEmpty(), "Fixture did not reproduce URI-scoped listener collision");
			fullModel.updateWith(model("Game of Life"), true);
			final int before = callbacks[0];
			check(fullModel.getValidationContext() != snippet.getValidationContext(), "Shared validation context");
			snippet.updateWith(model(), true);
			snippet.updateWith(null, true);
			check(callbacks[0] == before && toolbarExperiments.equals(List.of("Game of Life")),
					"Snippet validation overwrote the Run list");

			fullModel.updateWith(model(), true);
			check(toolbarExperiments.isEmpty(), "Removing the real experiment did not update the Run list");
			fullModel.updateWith(model("Game of Life", "Batch"), true);
			check(toolbarExperiments.equals(List.of("Game of Life", "Batch")), "Real model updates were suppressed");

			final boolean[] disposed = { false };
			final GamlResource diagramResource = resource(fullModelURI,
					model(disposed, "Game of Life", "Batch", "abstract:base"));
			final List<String> directNames = new ArrayList<>();
			final GamlEditorState[] toolbarSnapshot = new GamlEditorState[1];
			final int beforeDirectValidation = callbacks[0];
			check(ModelGenerator.validateDiagramResource(diagramResource, (model, experiments, status) -> {
				check(!disposed[0], "Notified after disposal");
				toolbarSnapshot[0] = new GamlEditorState(status, experiments);
				for (final IDescription experiment : experiments) { directNames.add(experiment.getName()); }
			}).isEmpty(), "Unexpected validation errors");
			check(directNames.equals(List.of("Game of Life", "Batch")), "Direct validation lost runnable experiments");
			check(disposed[0], "Description not disposed");
			check(toolbarSnapshot[0].experiments.equals(List.of("Game of Life", "Batch")),
					"Toolbar snapshot did not survive description disposal");
			check(callbacks[0] == beforeDirectValidation, "Diagram validation notified another URI listener");
			ModelGenerator.validateDiagramResource(resource(fullModelURI), (model, experiments, status) -> {
				check(model == null && !experiments.iterator().hasNext(), "Failed model was reported as valid");
			});
		} finally {
			GamlResourceServices.removeResourceListener(fullModelURI);
			GamlResourceServices.discardValidationContext(fullModel);
			GamlResourceServices.discardValidationContext(snippet);
		}
		System.out.println("PASS: reproduced URI collision; isolated snippet callbacks and validation contexts; "
				+ "direct editor notification, abstract filtering and description lifecycle");
	}
}
