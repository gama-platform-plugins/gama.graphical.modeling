package gama.ui.diagram.swt.editFrame;

import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.PictogramsFactory;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.xtext.parser.IParser;

import gama.ui.diagram.metamodel.GamaPackage;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.features.modelgeneration.DescriptionDiagramImporterTest;
import gama.ui.diagram.features.modelgeneration.DiagramModelDocumentation;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gaml.compiler.GamlStandaloneSetup;
import gaml.compiler.ui.wizards.species.ModelDocumentation;

/** Parse unchanged previews from a saved diagram or a source-backed import; never writes the input file. */
public final class DiagramWizardSourceTest {
	public static void main(final String[] args) throws Exception {
		if (args.length == 0 || "--source".equals(args[0]) && args.length < 2) {
			throw new IllegalArgumentException("Expected diagram.gadl [output-directory] or --source model.gaml [output-directory]");
		}
		GamaPackage.eINSTANCE.eClass();
		org.eclipse.graphiti.mm.pictograms.PictogramsPackage.eINSTANCE.eClass();
		final var parser = GamlStandaloneSetup.doSetup().getInstance(IParser.class);
		final boolean sourceMode = "--source".equals(args[0]);
		final Path input = Path.of(args[sourceMode ? 1 : 0]);
		final int outputIndex = sourceMode ? 2 : 1;
		final var resource = new XMIResourceImpl(URI.createFileURI(input.toAbsolutePath().toString()));
		java.util.List<String> expectedEquations = null;
		Diagram diagram;
		if (sourceMode) {
			final String source = Files.readString(input);
			expectedEquations = equations(parser, source);
			final var model = DescriptionDiagramImporterTest.importSource(parser, source);
			diagram = PictogramsFactory.eINSTANCE.createDiagram();
			diagram.setName(model.getName());
			DiagramModelDocumentation.write(diagram, ModelDocumentation.read(source));
			resource.getContents().add(diagram);
			resource.getContents().add(model);
			for (final var object : model.getObjects()) {
				if (!(object instanceof ESpecies)) continue;
				final var shape = PictogramsFactory.eINSTANCE.createContainerShape();
				diagram.getChildren().add(shape);
				final var link = PictogramsFactory.eINSTANCE.createPictogramLink();
				link.getBusinessObjects().add(object);
				shape.setLink(link);
				diagram.getPictogramLinks().add(link);
			}
			// Include the real persistence boundary before editing the imported diagram.
			final var bytes = new java.io.ByteArrayOutputStream();
			resource.save(bytes, Map.of());
			resource.unload();
			resource.load(new java.io.ByteArrayInputStream(bytes.toByteArray()), Map.of());
			diagram = (Diagram) resource.getContents().get(0);
		} else {
			try (final var stream = Files.newInputStream(input)) {
				resource.load(stream, Map.of());
			}
			diagram = (Diagram) resource.getContents().get(0);
		}
		final var fp = (IFeatureProvider) Proxy.newProxyInstance(IFeatureProvider.class.getClassLoader(),
				new Class<?>[] { IFeatureProvider.class }, (proxy, method, arguments) -> {
					if (method.getName().equals("getBusinessObjectForPictogramElement")) {
						return Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement(
								(PictogramElement) arguments[0]);
					}
					throw new UnsupportedOperationException(method.getName());
				});
		int previews = 0;
		final String original = ModelGenerator.generateModel(fp, diagram, null);
		for (final var shape : diagram.getChildren()) {
			if (!(fp.getBusinessObjectForPictogramElement(shape) instanceof ESpecies species)) continue;
			final String source;
			if (species instanceof EExperiment experiment) {
				source = ExperimentWizardAdapter.candidateModel(fp, diagram, experiment, null,
						ExperimentWizardAdapter.snapshot(experiment));
			} else {
				final var draft = species instanceof EWorldAgent world
						? SpeciesWizardAdapter.modelSnapshot(diagram, world) : SpeciesWizardAdapter.snapshot(species);
				source = SpeciesWizardAdapter.candidateModel(fp, diagram, species, null, draft);
			}
			if (args.length > outputIndex) {
				Files.writeString(Path.of(args[outputIndex], "preview-" + previews + ".gaml"), source);
			}
			if (!source.equals(original)) {
				throw new AssertionError(species.getName() + " unchanged preview lost stored source");
			}
			if (expectedEquations != null && !expectedEquations.equals(equations(parser, source))) {
				throw new AssertionError(species.getName() + " preview lost or changed source differential equations");
			}
			final var parsed = parser.parse(new StringReader(source));
			final var errors = new StringBuilder();
			for (final var error : parsed.getSyntaxErrors()) {
				errors.append("line ").append(error.getStartLine()).append(": ")
						.append(error.getSyntaxErrorMessage().getMessage()).append(" [")
						.append(error.getText()).append("]\n");
			}
			if (!errors.isEmpty()) throw new AssertionError(species.getName() + " preview:\n" + errors);
			previews++;
		}
		if (previews == 0) throw new AssertionError("No species/world/grid previews exercised");
		System.out.println("PASS: " + previews + " unchanged wizard previews parse after "
				+ (sourceMode ? "source-backed import and XMI save/reload" : "loading saved diagram"));
	}

	private static java.util.List<String> equations(final IParser parser, final String source) {
		final var assignments = new java.util.ArrayList<String>();
		final var root = parser.parse(new StringReader(source)).getRootASTElement();
		for (final var iterator = root.eAllContents(); iterator.hasNext();) {
			if (!(iterator.next() instanceof gaml.compiler.gaml.S_Equations system)) continue;
			for (final var equation : system.getEquations()) {
				assignments.add(org.eclipse.xtext.nodemodel.util.NodeModelUtils.getTokenText(
						org.eclipse.xtext.nodemodel.util.NodeModelUtils.findActualNodeFor(equation)));
			}
		}
		return assignments.stream().sorted().toList();
	}
}
