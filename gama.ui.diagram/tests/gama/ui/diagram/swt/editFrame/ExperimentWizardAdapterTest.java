package gama.ui.diagram.swt.editFrame;

import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.PictogramsFactory;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.xtext.parser.IParser;

import gama.ui.diagram.features.modelgeneration.DescriptionDiagramImporterTest;
import gama.ui.diagram.features.modelgeneration.ExperimentKind;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EWorldAgent;
import gaml.compiler.GamlStandaloneSetup;
import gaml.compiler.ui.wizards.experiment.ExperimentDraft;
import gaml.compiler.ui.wizards.experiment.ExperimentDraft.Monitor;
import gaml.compiler.ui.wizards.experiment.ExperimentDraft.Parameter;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Facet;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Variable;

public final class ExperimentWizardAdapterTest {
	private static int checks;

	private static void check(final boolean value, final String message) {
		checks++;
		if (!value) throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		final var parser = new GamlStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
		final var model = DescriptionDiagramImporterTest.importSource(parser, """
				model experiments
				global { int population <- 10; }
				experiment interactive type:gui autorun:false {
					int local_counter <- 1;
					init { local_counter <- 2; }
					action reset() { local_counter <- 0; }
					parameter "Population" var:population min:1 max:100 category:"Inputs" unit:"agents";
					parameter "Say \\"hi\\"" var:population;
					output {
						monitor "Count" value:population refresh:10 color:#blue;
						monitor 'Say "hi"' value:population;
						display map { }
					}
				}
				experiment exploration type:batch repeat:2 until:cycle > 5 { method exhaustive; }
				experiment checks type:"test" { test "positive" { assert population > 0; } }
				""");
		final var diagram = Graphiti.getPeCreateService().createDiagram("gama", "experiments", true);
		final var resource = new XMIResourceImpl(URI.createURI("memory:/experiments.gadl"));
		resource.getContents().addAll(List.of(diagram, model));
		final var world = (EWorldAgent) model.getObjects().stream().filter(EWorldAgent.class::isInstance).findFirst().orElseThrow();
		final var shape = Graphiti.getPeCreateService().createContainerShape(diagram, true);
		final var link = PictogramsFactory.eINSTANCE.createPictogramLink();
		link.getBusinessObjects().add(world);
		shape.setLink(link);
		diagram.getPictogramLinks().add(link);
		final var fp = (IFeatureProvider) Proxy.newProxyInstance(IFeatureProvider.class.getClassLoader(),
				new Class<?>[] { IFeatureProvider.class }, (proxy, method, arguments) -> {
					if ("getBusinessObjectForPictogramElement".equals(method.getName())) {
						return Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement((PictogramElement) arguments[0]);
					}
					throw new UnsupportedOperationException(method.getName());
				});
		final String original = ModelGenerator.generateModel(fp, diagram, null);
		check(!parser.parse(new StringReader(original)).hasSyntaxErrors(), "Imported experiments generate invalid GAML");
		final var experiments = model.getObjects().stream().filter(EExperiment.class::isInstance).map(EExperiment.class::cast).toList();
		check(ExperimentWizardAdapter.parentNames(diagram, null).equals(List.of("checks", "exploration", "interactive")),
				"Creation parent suggestions must include existing experiments only");
		for (final var experiment : experiments) {
			final var initial = ExperimentWizardAdapter.snapshot(experiment);
			final var initContext = ExperimentWizardAdapter.bodyContext(fp, diagram, experiment, null, initial);
			if (!initial.init().isBlank()) {
				check(original.equals(initContext.prefix() + initial.init() + initContext.suffix()),
						"Experiment init context changed existing source");
			}
			check(!parser.parse(new StringReader(initContext.prefix() + "write 1;" + initContext.suffix())).hasSyntaxErrors(),
					"Experiment init editor lacks an editable statement context");
			check(initial.type().equals(ExperimentKind.of(experiment)), "Snapshot changed experiment type");
			final String preview = ExperimentWizardAdapter.candidateModel(fp, diagram, experiment, null, initial);
			check(original.equals(preview), "No-op " + experiment.getName() + " experiment preview changed source.\nBefore:\n"
					+ original + "\nAfter:\n" + preview);
			check(!parser.parse(new StringReader(preview)).hasSyntaxErrors(), "Experiment preview invalid");
			check(initial.equals(ExperimentWizardAdapter.snapshot(experiment)), "Preview mutated live experiment");
		}
		final var experiment = experiments.stream().filter(e -> "interactive".equals(e.getName())).findFirst().orElseThrow();
		final var initial = ExperimentWizardAdapter.snapshot(experiment);
		check("Say \"hi\"".equals(initial.parameters().get(1).title())
				&& "Say \"hi\"".equals(initial.monitors().get(1).title()),
				"Source labels were not unquoted/unescaped: " + initial.parameters() + " / " + initial.monitors());
		check(initial.parameters().get(0).facets().stream().anyMatch(f -> "unit".equals(f.name())), "Parameter unit lost");
		check(initial.monitors().get(0).facets().stream().anyMatch(f -> "refresh".equals(f.name())), "Monitor refresh lost");
		final var parameter = experiment.getParameters().get(0);
		final var monitor = experiment.getMonitors().get(0);
		final var variable = experiment.getVariables().get(0);
		final var action = experiment.getActionLinks().get(0);
		final var display = experiment.getDisplayLinks().get(0);
		final var experimentLink = experiment.getExperimentLink();
		final var typeFacet = experiment.getFacets().stream().filter(f -> "type".equals(f.getName())).findFirst().orElseThrow();
		final var creation = new org.eclipse.graphiti.features.context.impl.CreateConnectionContext();
		final var worldAnchor = Graphiti.getPeCreateService().createChopboxAnchor(shape);
		creation.setSourceAnchor(worldAnchor);
		for (final var tool : List.of(
				new gama.ui.diagram.features.create.CreateGuiExperimentLinkFeature(fp),
				new gama.ui.diagram.features.create.CreateBatchExperimentLinkFeature(fp),
				new gama.ui.diagram.features.create.CreateTestExperimentLinkFeature(fp))) {
			check(tool.canCreate(creation) && tool.canStartConnection(creation), "World cannot create experiment");
			creation.setSourceAnchor(null);
			check(tool.create(creation) == null && !tool.hasDoneChanges(), "Invalid experiment creation reports a change");
			creation.setSourceAnchor(worldAnchor);
		}
		final var diagramType = (org.eclipse.graphiti.dt.IDiagramTypeProvider) Proxy.newProxyInstance(
				org.eclipse.graphiti.dt.IDiagramTypeProvider.class.getClassLoader(),
				new Class<?>[] { org.eclipse.graphiti.dt.IDiagramTypeProvider.class },
				(proxy, method, arguments) -> {
					if ("getDiagram".equals(method.getName())) return diagram;
					throw new UnsupportedOperationException(method.getName());
				});
		check(java.util.Arrays.stream(new gama.ui.diagram.editor.GamaFeatureProvider(diagramType).getCreateConnectionFeatures())
				.anyMatch(f -> f instanceof gama.ui.diagram.features.create.CreateTestExperimentLinkFeature),
				"Test experiment is missing from palette services");
		final var changed = new ExperimentDraft("interactive", "gui", List.of(new Facet("autorun", "true")),
				List.of(new Variable("local_counter", "int", "3", "", "", "", "")), "local_counter <- 4;",
				List.of(new Parameter("Population", "population",
						List.of(new Facet("min", "2"), new Facet("category", "\"Inputs\""), new Facet("unit", "\"agents\"")))),
				List.of(new Monitor("Count", "population + local_counter",
						List.of(new Facet("refresh", "5"), new Facet("color", "#red")))));
		final var domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		try {
			domain.getResourceSet().getResources().add(resource);
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { ExperimentWizardAdapter.apply(experiment, changed); }
			});
			check(changed.equals(ExperimentWizardAdapter.snapshot(experiment)), "Experiment edit did not round-trip");
			check(experiment.getParameters().get(0) == parameter && experiment.getMonitors().get(0) == monitor
					&& experiment.getVariables().get(0) == variable && experiment.getActionLinks().get(0) == action
					&& experiment.getDisplayLinks().get(0) == display && experiment.getExperimentLink() == experimentLink,
					"Experiment edit replaced member/display/link identities");
			check(experiment.getFacets().contains(typeFacet), "Experiment edit replaced unchanged type facet");
			check(!parser.parse(new StringReader(ModelGenerator.generateModel(fp, diagram, null))).hasSyntaxErrors(),
					"Experiment edit generated invalid GAML");
			domain.getCommandStack().undo();
			check(initial.equals(ExperimentWizardAdapter.snapshot(experiment)), "Experiment undo incomplete");
			domain.getCommandStack().redo();
			check(changed.equals(ExperimentWizardAdapter.snapshot(experiment)), "Experiment redo incomplete");
			domain.getCommandStack().undo();
			final String label = "Count \"quoted\" 'single' \\ path";
			final var escaped = new ExperimentDraft(initial.name(), initial.type(), initial.facets(),
					initial.variables(), initial.init(),
					List.of(new Parameter(label, "population", List.of(new Facet("category", "string(population)")))),
					List.of(new Monitor(label, "population", initial.monitors().get(0).facets())));
			final String escapedSource = ExperimentWizardAdapter.candidateModel(fp, diagram, experiment, null, escaped);
			check(escapedSource.contains("category:string(population)")
					&& escapedSource.contains(gama.api.utils.StringUtils.toGamlString(label)),
					"Label escaping or raw expression facets changed");
			check(!parser.parse(new StringReader(escapedSource)).hasSyntaxErrors(), "Escaped labels produce invalid GAML");
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { ExperimentWizardAdapter.apply(experiment, escaped); }
			});
			check(experiment.getParameters().get(0) == parameter, "Renaming parameter label replaced its object");
			check(escaped.equals(ExperimentWizardAdapter.snapshot(experiment)), "Raw category expression was quoted or lost");
			domain.getCommandStack().undo();
			final var switchWithDisplays = new ExperimentDraft("interactive", "batch", List.of(), List.of(), "", List.of(), List.of());
			check(ExperimentWizardAdapter.candidateModel(fp, diagram, experiment, null, switchWithDisplays)
					.contains("display map"), "Type switch discarded displays");
			for (final var existing : experiments) {
				if (existing == experiment) continue;
				final var switchWithBlocks = new ExperimentDraft(existing.getName(), "gui", List.of(), List.of(), "", List.of(), List.of());
				check(ExperimentWizardAdapter.candidateModel(fp, diagram, existing, null, switchWithBlocks)
						.contains(existing.getGamlCode()), "Kind switch discarded type-specific code");
			}
			for (final String type : List.of("gui", "batch", "test")) {
				final var draft = ExperimentDraft.empty(type);
				final int count = world.getExperimentLinks().size();
				final String preview = ExperimentWizardAdapter.candidateModel(fp, diagram, null, world, draft);
				check(preview.contains("type:" + type) && world.getExperimentLinks().size() == count,
						"Creation preview altered model or omitted type");
				check(!parser.parse(new StringReader(preview)).hasSyntaxErrors(), "Experiment creation has invalid syntax");
				final var created = gama.ui.diagram.metamodel.GamaFactory.eINSTANCE.createEGUIExperiment();
				domain.getCommandStack().execute(new RecordingCommand(domain) {
					@Override protected void doExecute() {
						ExperimentWizardAdapter.attach(world, created);
						ExperimentWizardAdapter.apply(created, draft);
					}
				});
				check(type.equals(ExperimentKind.of(created)) && created.getModel() == model, "Creation lost kind or containment");
				domain.getCommandStack().undo();
				check(created.eResource() == null && world.getExperimentLinks().size() == count, "Creation undo left linked objects");
				domain.getCommandStack().redo();
				check(created.getExperimentLink().getSpecies() == world, "Creation redo lost world");
				domain.getCommandStack().undo();
			}
			final var batch = experiments.stream().filter(e -> "exploration".equals(e.getName())).findFirst().orElseThrow();
			final var test = experiments.stream().filter(e -> "checks".equals(e.getName())).findFirst().orElseThrow();
			final var testInitial = ExperimentWizardAdapter.snapshot(test);
			final var inherited = new ExperimentDraft(testInitial.name(), "test", List.of(new Facet("parent", batch.getName())),
					testInitial.variables(), testInitial.init(), testInitial.parameters(), testInitial.monitors());
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { ExperimentWizardAdapter.apply(test, inherited); }
			});
			check(test.getInheritsFrom() == batch && batch.getInheritingLinks().get(0).getChild() == test,
					"Experiment parent facet did not update references");
			check(ExperimentWizardAdapter.parentNames(diagram, batch).equals(List.of("interactive")),
					"Parent suggestions include self or descendant");
			check(ExperimentWizardAdapter.parentNames(diagram, test).equals(List.of("exploration", "interactive")),
					"Parent suggestions lost valid ancestors or unrelated experiments");
			final var batchInitial = ExperimentWizardAdapter.snapshot(batch);
			final var cyclic = new ExperimentDraft(batchInitial.name(), "batch",
					List.of(new Facet("parent", test.getName())), batchInitial.variables(), batchInitial.init(),
					batchInitial.parameters(), batchInitial.monitors());
			check(ExperimentWizardAdapter.draftError(batch, cyclic) != null, "Experiment inheritance cycle accepted");
			check(ExperimentWizardAdapter.candidateModel(fp, diagram, test, null, inherited)
					.equals(ModelGenerator.generateModel(fp, diagram, null)), "Inherited experiment no-op changed source");
			domain.getCommandStack().undo();
			check(test.getInheritsFrom() == null && batch.getInheritingLinks().isEmpty(), "Parent undo left references");
			final var legacyBatch = gama.ui.diagram.metamodel.GamaFactory.eINSTANCE.createEBatchExperiment();
			legacyBatch.setName("legacy");
			final var displayLink = gama.ui.diagram.metamodel.GamaFactory.eINSTANCE.createEDisplayLink();
			displayLink.setExperiment(legacyBatch);
			ExperimentKind.set(legacyBatch, "gui");
			check(displayLink.getExperiment() == legacyBatch
					&& gama.ui.diagram.metamodel.GamaPackage.Literals.EDISPLAY_LINK__EXPERIMENT.getEReferenceType()
							.isInstance(legacyBatch), "Legacy batch to GUI cannot retain display links");
			final var legacyShape = PictogramsFactory.eINSTANCE.createContainerShape();
			final var legacyDiagram = Graphiti.getPeCreateService().createDiagram("gama", "legacy", true);
			final var legacyResource = new XMIResourceImpl(URI.createURI("memory:/legacy.gadl"));
			legacyResource.getContents().addAll(List.of(legacyDiagram, legacyBatch));
			legacyDiagram.getChildren().add(legacyShape);
			final var legacyLink = PictogramsFactory.eINSTANCE.createPictogramLink();
			legacyLink.getBusinessObjects().add(legacyBatch);
			legacyShape.setLink(legacyLink);
			legacyDiagram.getPictogramLinks().add(legacyLink);
			creation.setSourceAnchor(Graphiti.getPeCreateService().createChopboxAnchor(legacyShape));
			final var displayTool = new gama.ui.diagram.features.create.CreateDisplayLinkFeature(fp);
			check(displayTool.canCreate(creation), "Legacy batch switched to GUI cannot create a display");
			for (final String kind : List.of("batch", "test")) {
				ExperimentKind.set(legacyBatch, kind);
				check(!displayTool.canCreate(creation) && !displayTool.canStartConnection(creation),
						"Display creation ignores experiment semantic type");
			}
			final var bytes = new java.io.ByteArrayOutputStream();
			resource.save(bytes, Map.of());
			final var reloaded = new XMIResourceImpl();
			reloaded.load(new java.io.ByteArrayInputStream(bytes.toByteArray()), Map.of());
			final var all = reloaded.getAllContents();
			int restored = 0;
			while (all.hasNext()) if (all.next() instanceof EExperiment saved) {
				final var source = experiments.stream().filter(e -> e.getName().equals(saved.getName())).findFirst().orElseThrow();
				check(ExperimentWizardAdapter.snapshot(source).equals(ExperimentWizardAdapter.snapshot(saved)), "XMI lost experiment draft");
				check(java.util.Objects.equals(source.getGamlCode(), saved.getGamlCode()), "XMI lost test/method blocks");
				restored++;
			}
			check(restored == 3 && original.equals(ModelGenerator.generateModel(fp, diagram, null)), "Undo/save lost experiments");
		} finally {
			domain.dispose();
		}
		System.out.println("PASS: " + checks + " experiment kinds, fields, facets, source, creation, persistence, identity and undo checks");
	}
}
