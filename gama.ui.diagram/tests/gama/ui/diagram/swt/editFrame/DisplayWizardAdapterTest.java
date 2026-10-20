package gama.ui.diagram.swt.editFrame;

import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.xtext.parser.IParser;

import gama.ui.diagram.features.modelgeneration.DescriptionDiagramImporterTest;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EDisplay;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.metamodel.GamaFactory;
import gaml.compiler.GamlStandaloneSetup;
import gaml.compiler.ui.wizards.display.DisplayDraft;
import gaml.compiler.ui.wizards.display.DisplaySource;
import gaml.compiler.ui.wizards.display.LayerDraft;
import gaml.compiler.ui.wizards.display.LayerKind;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Facet;

public final class DisplayWizardAdapterTest {
	private static int checks;

	private static void check(final boolean value, final String message) {
		checks++;
		if (!value) throw new AssertionError(message);
	}

	private static DisplayDraft layers(final DisplayDraft draft, final List<LayerDraft> layers) {
		return new DisplayDraft(draft.name(), draft.renderer(), draft.facets(), layers, draft.trailingSource());
	}

	public static void main(final String[] args) throws Exception {
		final var parser = new GamlStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
		final var model = DescriptionDiagramImporterTest.importSource(parser, """
				model displays
				global { int number <- 2; }
				species people { aspect default { draw circle(1); } aspect highlighted { draw circle(2); } }
				grid cells width:2 height:2;
				experiment run type:gui {
					output {
						display "People's \\"view\\"" type:java2D background:#white {
							// first layer comment
							species people aspect:default;
							grid cells border:#gray;
							agents "selected" value:people aspect:default;
							image "test.png" refresh:false;
							graphics "caption" { draw "Population" color:#black at:{0,0}; }
							// keep chart bytes and internal comments
							chart "Size" type:series { data "N" value:number; }
							// trailing comment
						}
						display other {}
					}
				}
				""");
		final var diagram = Graphiti.getPeCreateService().createDiagram("gama", "displays", true);
		final var resource = new XMIResourceImpl(URI.createURI("memory:/displays.gadl"));
		resource.getContents().addAll(List.of(diagram, model));
		final var world = (EWorldAgent) model.getObjects().stream().filter(EWorldAgent.class::isInstance).findFirst().orElseThrow();
		final var experiment = (EExperiment) model.getObjects().stream().filter(EExperiment.class::isInstance).findFirst().orElseThrow();
		final var display = experiment.getDisplayLinks().get(0).getDisplay();
		final var shape = Graphiti.getPeCreateService().createContainerShape(diagram, true);
		final var pictogram = org.eclipse.graphiti.mm.pictograms.PictogramsFactory.eINSTANCE.createPictogramLink();
		pictogram.getBusinessObjects().add(world);
		shape.setLink(pictogram);
		diagram.getPictogramLinks().add(pictogram);
		final var fp = (IFeatureProvider) Proxy.newProxyInstance(IFeatureProvider.class.getClassLoader(),
				new Class<?>[] { IFeatureProvider.class }, (proxy, method, arguments) -> {
					if ("getBusinessObjectForPictogramElement".equals(method.getName())) {
						return Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement((PictogramElement) arguments[0]);
					}
					throw new UnsupportedOperationException(method.getName());
				});
		final String original = ModelGenerator.generateModel(fp, diagram, null);
		final var initial = DisplayWizardAdapter.snapshot(display);
		check("People's \"view\"".equals(initial.name()), "Display label was not decoded");
		check(initial.layers().size() == 6, "Imported layers not exposed");
		check(initial.layers().stream().map(LayerDraft::kind).toList().equals(
				List.of(LayerKind.SPECIES, LayerKind.GRID, LayerKind.AGENTS, LayerKind.IMAGE, LayerKind.TEXT, LayerKind.RAW)),
				"Common layers or opaque chart not classified");
		check(initial.trailingSource().contains("// trailing comment"), "Trailing comments lost");
		check(initial.layers().get(0).source().contains("// first layer comment"), "Attached comment lost");
		check(initial.layers().get(5).source().contains("data \"N\" value:number;"), "Opaque chart lost");
		check(original.equals(DisplayWizardAdapter.candidateModel(fp, diagram, display, null, initial)), "No-op changed source");
		check(display.isDefineGamlCode(), "Preview converted live source");
		check(original.equals(DisplayWizardAdapter.context(fp, diagram, display, null, initial).prefix()),
				"Full-model body context missing");
		check(DisplayWizardAdapter.context(fp, diagram, display, null, initial).suffix().isEmpty(), "Unexpected context suffix");
		check(DisplayWizardAdapter.speciesNames(diagram, false).containsAll(List.of("people", "cells")), "Species suggestions missing");
		check(DisplayWizardAdapter.speciesNames(diagram, true).equals(List.of("cells")), "Grid suggestions contain non-grids");
		check(DisplayWizardAdapter.aspectNames(diagram, "people").equals(List.of("default", "highlighted")),
				"Species aspect suggestions missing");
		check(DisplayWizardAdapter.aspectNames(diagram, "cells").isEmpty(), "Undeclared default aspect suggested");
		final var duplicateName = new DisplayDraft("other", initial.renderer(), initial.facets(), initial.layers(), initial.trailingSource());
		check(DisplayWizardAdapter.validate(fp, diagram, display, null, duplicateName).contains("already exists"), "Duplicate display name accepted");
		final var kindChange = new ArrayList<>(initial.layers());
		final var first = initial.layers().get(0);
		kindChange.set(0, new LayerDraft(first.id(), LayerKind.GRID, first.expression(), first.facets(), List.of(), first.source(), first.body()));
		check(DisplayWizardAdapter.validate(fp, diagram, display, null, layers(initial, kindChange)).contains("cannot change"),
				"Existing layer kind conversion not refused");
		final var invalidRaw = LayerDraft.empty(LayerKind.RAW);
		final var invalid = new DisplayDraft("Should not be applied", "opengl", List.of(),
				List.of(new LayerDraft(invalidRaw.id(), LayerKind.RAW, "", List.of(), List.of(), "chart {")), "");
		check(DisplayWizardAdapter.validate(fp, diagram, display, null, invalid) != null, "Malformed layer source accepted");
		try {
			DisplayWizardAdapter.apply(display, invalid);
			throw new AssertionError("Malformed layer source did not fail explicitly");
		} catch (final IllegalArgumentException expected) {
			check(original.equals(ModelGenerator.generateModel(fp, diagram, null)), "Invalid apply partially changed live header");
		}
		final var domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		try {
			domain.getResourceSet().getResources().add(resource);
			final var relation = display.getDisplayLink();
			final var headerFacet = display.getFacets().stream().filter(f -> "background".equals(f.getName())).findFirst().orElseThrow();
			final var renamed = new DisplayDraft("Renamed", "opengl", initial.facets(), initial.layers(), initial.trailingSource());
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { DisplayWizardAdapter.apply(display, renamed); }
			});
			check(display.isDefineGamlCode() && display.getGamlCode().equals(ModelGenerator.displayBody(display)),
					"Header-only edit converted or altered raw body");
			check(display.getDisplayLink() == relation && display.getFacets().contains(headerFacet), "Header edit replaced identities");
			domain.getCommandStack().undo();
			check(original.equals(ModelGenerator.generateModel(fp, diagram, null)), "Header undo incomplete");
			final var reordered = new ArrayList<>(initial.layers());
			java.util.Collections.swap(reordered, 0, 1);
			reordered.add(initial.layers().get(0).duplicate());
			final var changed = layers(initial, reordered);
			final String candidate = DisplayWizardAdapter.candidateModel(fp, diagram, display, null, changed);
			check(!parser.parse(new StringReader(candidate)).hasSyntaxErrors(), "Reordered preview is invalid GAML");
			check(original.equals(ModelGenerator.generateModel(fp, diagram, null)), "Detached preview mutated live diagram");
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { DisplayWizardAdapter.apply(display, changed); }
			});
			check(!display.isDefineGamlCode() && display.getLayers().size() == 7, "Body edit did not materialize layers");
			check(candidate.equals(ModelGenerator.generateModel(fp, diagram, null)), "Accepted body differs from preview");
			check(ModelGenerator.displayBody(display).equals(DisplaySource.renderBody(changed.layers(), changed.trailingSource(), parser)),
					"Diagram body differs from shared layer rendering");
			check(display.getTrailingSource().contains("// trailing comment"), "Accepted body lost trailing source");
			check(display.getDisplayLink() == relation, "Body edit replaced relationship");
			domain.getCommandStack().undo();
			check(initial.equals(DisplayWizardAdapter.snapshot(display))
					&& original.equals(ModelGenerator.generateModel(fp, diagram, null)), "Body edit undo incomplete");
			domain.getCommandStack().redo();
			final var materialized = DisplayWizardAdapter.snapshot(display);
			final var objects = new ArrayList<>(display.getLayers());
			for (int i = 0; i < materialized.layers().size(); i++) {
				final var row = materialized.layers().get(i);
				final var edited = new LayerDraft(row.id(), row.kind(),
						switch (row.kind()) {
							case IMAGE -> "\"new.png\"";
							case TEXT -> "\"Updated label\"";
							case AGENTS -> "\"Updated agents\"";
							default -> row.expression();
						}, row.facets(), row.textFacets(),
						row.kind() == LayerKind.RAW ? row.source().replace("\"Size\"", "\"Updated chart\"") : row.source(),
						row.body());
				final var rows = new ArrayList<>(materialized.layers());
				rows.set(i, edited);
				final String preview = DisplayWizardAdapter.candidateModel(fp, diagram, display, null, layers(materialized, rows));
				check(!parser.parse(new StringReader(preview)).hasSyntaxErrors(), "Edited " + row.kind() + " no longer parses");
				check(display.getLayers().equals(objects), "Layer preview changed identities");
			}
			final var secondOrder = new ArrayList<>(materialized.layers());
			java.util.Collections.reverse(secondOrder);
			final var second = layers(materialized, secondOrder);
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { DisplayWizardAdapter.apply(display, second); }
			});
			final var reversedObjects = new ArrayList<>(objects);
			java.util.Collections.reverse(reversedObjects);
			check(display.getLayers().equals(reversedObjects), "Reordering replaced existing layers");
			final var current = DisplayWizardAdapter.snapshot(display);
			final var withoutFirst = layers(current, current.layers().subList(1, current.layers().size()));
			final var removed = display.getLayers().get(0);
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { DisplayWizardAdapter.apply(display, withoutFirst); }
			});
			check(removed.eResource() == null && !display.getLayers().contains(removed), "Removed row left an orphan");
			domain.getCommandStack().undo();
			check(display.getLayers().get(0) == removed && removed.eResource() == resource, "Remove undo did not restore row");
			final String accepted = ModelGenerator.generateModel(fp, diagram, null);
			final var bytes = new java.io.ByteArrayOutputStream();
			resource.save(bytes, Map.of());
			final var loaded = new XMIResourceImpl();
			loaded.load(new java.io.ByteArrayInputStream(bytes.toByteArray()), Map.of());
			final var copy = (gama.ui.diagram.metamodel.EGamaModel) loaded.getContents().get(1);
			final var restored = copy.getObjects().stream().filter(EDisplay.class::isInstance).map(EDisplay.class::cast)
					.filter(d -> d.getName().equals(initial.name())).findFirst().orElseThrow();
			check(ModelGenerator.displayBody(display).equals(ModelGenerator.displayBody(restored)), "XMI lost layer/trailing source");
			check(EcoreUtil.copy(display).getTrailingSource().equals(display.getTrailingSource()), "Copy lost trailing source");
			check(!parser.parse(new StringReader(accepted)).hasSyntaxErrors(), "Accepted source does not parse");
			if (args.length > 0) java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]), accepted);
			final var emptySpecies = LayerDraft.empty(LayerKind.SPECIES);
			final var creation = new DisplayDraft("Created", "java2D", List.of(),
					List.of(new LayerDraft(emptySpecies.id(), LayerKind.SPECIES, "people", List.of(), List.of(), "")),
					"\n// created end\n");
			final String createdSource = DisplayWizardAdapter.candidateModel(fp, diagram, null, experiment, creation);
			check(createdSource.contains("Created") && !parser.parse(new StringReader(createdSource)).hasSyntaxErrors(),
					"Display creation preview is invalid");
			check(accepted.equals(ModelGenerator.generateModel(fp, diagram, null)), "Creation preview mutated diagram");
			final var created = GamaFactory.eINSTANCE.createEDisplay();
			final int displayCount = experiment.getDisplayLinks().size();
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() {
					DisplayWizardAdapter.attach(experiment, created);
					DisplayWizardAdapter.apply(created, creation);
				}
			});
			check(created.getDisplayLink().getExperiment() == experiment && created.getLayers().size() == 1,
					"Accepted creation lost owner or layers");
			check(createdSource.equals(ModelGenerator.generateModel(fp, diagram, null)), "Creation differs from preview");
			domain.getCommandStack().undo();
			check(created.eResource() == null && experiment.getDisplayLinks().size() == displayCount
					&& accepted.equals(ModelGenerator.generateModel(fp, diagram, null)), "Creation undo left objects or source changes");
		} finally {
			domain.dispose();
		}
		legacyLayers(parser);
		layerBoundaries(parser);
		organizingMixedLayers(parser);
		System.out.println("PASS: " + checks + " display/layer import, source, ordering, identity, creation, undo and persistence checks");
	}

	private static void organizingMixedLayers(final IParser parser) throws Exception {
		final String body = """

				// background image
				image "background.png" refresh:false;
				// elevation mesh
				mesh [[1.0,2.0],[3.0,4.0]];
				// display definitions ordered alongside layers
				camera "main" location:{10,10,10} target:{0,0,0};
				light "ambient" type:ambient intensity:0.5;
				rotation 0.0 axis:{0,0,1};
				// species-local aspect
				species people { aspect visible { draw circle(2); } }
				// selected agents drawing
				agents "chosen" value:people { draw square(1); }
				// chart stays opaque
				chart "History" type:series { data "N" value:cycle; }
				// interaction stays opaque
				event "mouse_down" { write "clicked"; }
				// arbitrary drawing stays intact
				graphics "drawing" { draw circle(1) color:#red; draw square(2) color:#blue; }
				// overlay body stays intact
				overlay "HUD" { draw "Cycle" at:{0,0}; }
				// display footer
				""";
		final var display = GamaFactory.eINSTANCE.createEDisplay();
		display.setName("organization");
		display.setDefineGamlCode(true);
		display.setGamlCode(body);
		final var resource = new XMIResourceImpl(URI.createURI("memory:/layer-organization.gadl"));
		resource.getContents().add(display);
		final var domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		try {
			domain.getResourceSet().getResources().add(resource);
			final var original = DisplayWizardAdapter.snapshot(display);
			check(original.layers().size() == 11, "Mixed display organization lost declaration boundaries");
			check(original.layers().stream().map(LayerDraft::kind).map(Enum::name).toList().equals(
					List.of("IMAGE", "MESH", "CAMERA", "LIGHT", "ROTATION", "SPECIES", "AGENTS", "RAW", "RAW",
							"GRAPHICS", "OVERLAY")), "Display declarations do not reach their kind-specific layer forms");
			check(original.layers().stream().anyMatch(row -> row.kind() == LayerKind.MESH),
					"Mesh layers are not exposed to the shared editor");
			for (final var row : original.layers()) {
				if (row.kind() == LayerKind.GRAPHICS || row.kind() == LayerKind.OVERLAY
						|| row.kind() == LayerKind.SPECIES || row.kind() == LayerKind.AGENTS) {
					check(!row.body().isBlank() && row.body().contains("draw "),
							"Diagram snapshot dropped the editable block body for " + row.kind());
				}
			}
			final var reordered = new ArrayList<>(original.layers());
			java.util.Collections.reverse(reordered);
			final var added = DisplaySource.parseLayer("\n// new foreground\nimage \"foreground.png\";",
					"new-foreground", parser);
			reordered.add(added);
			final var draft = layers(original, reordered);
			final String expected = DisplaySource.renderBody(draft.layers(), draft.trailingSource(), parser);
			check(display.isDefineGamlCode() && body.equals(display.getGamlCode()),
					"Organizing detached rows changed the source before acceptance");
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { DisplayWizardAdapter.apply(display, draft); }
			});
			check(expected.equals(ModelGenerator.displayBody(display)), "Accepted mixed layer order differs from preview");
			final var objects = List.copyOf(display.getLayers());
			check(objects.size() == 12, "Adding a layer dropped an existing chart/event/drawing");
			for (int i = 0; i < reordered.size(); i++) {
				check(ModelGenerator.defineLayer(objects.get(i)).contains(reordered.get(i).source().strip()),
						"Organization changed the contents or comments of layer " + i);
			}
			check(ModelGenerator.displayBody(display).endsWith(original.trailingSource()), "Organization lost trailing comments");
			domain.getCommandStack().undo();
			check(display.isDefineGamlCode() && body.equals(display.getGamlCode()) && display.getLayers().isEmpty(),
					"Undo did not restore raw display source");
			domain.getCommandStack().redo();
			check(display.getLayers().equals(objects), "Redo replaced the materialized layer objects");
			final var materialized = DisplayWizardAdapter.snapshot(display);
			final var reversed = new ArrayList<>(materialized.layers());
			java.util.Collections.reverse(reversed);
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { DisplayWizardAdapter.apply(display, layers(materialized, reversed)); }
			});
			final var reverseObjects = new ArrayList<>(objects);
			java.util.Collections.reverse(reverseObjects);
			check(display.getLayers().equals(reverseObjects), "Reordering mixed materialized layers replaced identities");
			final var beforeBodyEdit = DisplayWizardAdapter.snapshot(display);
			for (final var row : beforeBodyEdit.layers()) {
				if (row.body().isBlank()) continue;
				final var bodyRows = new ArrayList<>(beforeBodyEdit.layers());
				final String editedBody = "\n// edited " + row.kind() + " layer body\n" + row.body();
				bodyRows.set(bodyRows.indexOf(row), new LayerDraft(row.id(), row.kind(), row.expression(),
						row.facets(), row.textFacets(), row.source(), editedBody));
				domain.getCommandStack().execute(new RecordingCommand(domain) {
					@Override protected void doExecute() { DisplayWizardAdapter.apply(display, layers(beforeBodyEdit, bodyRows)); }
				});
				final var bodyResult = DisplayWizardAdapter.snapshot(display);
				check(bodyResult.layers().stream().filter(value -> value.id().equals(row.id())).findFirst().orElseThrow()
						.body().equals(editedBody), row.kind() + " block body was not independently editable");
				check(display.getLayers().equals(reverseObjects), row.kind() + " body edit replaced layer identities");
				for (int i = 0; i < beforeBodyEdit.layers().size(); i++) {
					if (!beforeBodyEdit.layers().get(i).id().equals(row.id())) {
						check(beforeBodyEdit.layers().get(i).equals(bodyResult.layers().get(i)),
								row.kind() + " body edit changed an adjacent layer");
					}
				}
				domain.getCommandStack().undo();
				check(beforeBodyEdit.equals(DisplayWizardAdapter.snapshot(display)), row.kind() + " body undo changed source");
			}
			final var graphics = beforeBodyEdit.layers().stream().filter(row -> row.kind() == LayerKind.GRAPHICS)
					.findFirst().orElseThrow();
			final var editedRows = new ArrayList<>(beforeBodyEdit.layers());
			editedRows.set(editedRows.indexOf(graphics), new LayerDraft(graphics.id(), graphics.kind(),
					graphics.expression(), graphics.facets(), graphics.textFacets(), graphics.source(),
					graphics.body().replace("circle(1)", "circle(3)")));
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { DisplayWizardAdapter.apply(display, layers(beforeBodyEdit, editedRows)); }
			});
			check(display.getLayers().equals(reverseObjects), "Editing a graphics body replaced layer identities");
			check(ModelGenerator.displayBody(display).contains("circle(3)"), "Shared layer body edit was not applied");
			final var afterBodyEdit = DisplayWizardAdapter.snapshot(display);
			for (int i = 0; i < beforeBodyEdit.layers().size(); i++) {
				if (beforeBodyEdit.layers().get(i).kind() == LayerKind.RAW) {
					check(beforeBodyEdit.layers().get(i).source().equals(afterBodyEdit.layers().get(i).source()),
							"Editing graphics changed neighboring chart/event source");
				}
			}
			domain.getCommandStack().undo();
			check(beforeBodyEdit.equals(DisplayWizardAdapter.snapshot(display)), "Graphics body undo lost draft/source data");
			domain.getCommandStack().redo();
			final var bytes = new java.io.ByteArrayOutputStream();
			resource.save(bytes, Map.of());
			final var loaded = new XMIResourceImpl();
			loaded.load(new java.io.ByteArrayInputStream(bytes.toByteArray()), Map.of());
			final var restored = (EDisplay) loaded.getContents().get(0);
			check(ModelGenerator.displayBody(display).equals(ModelGenerator.displayBody(restored)),
					"Saving organized chart/event/drawing layers lost source");
			check(DisplayWizardAdapter.snapshot(display).equals(DisplayWizardAdapter.snapshot(restored)),
					"Saving organized mixed layers lost their editable drafts");
			final String declaration = "model organization experiment run type:gui { output { display organization {"
					+ ModelGenerator.displayBody(restored) + "} } }";
			check(!parser.parse(new StringReader(declaration)).hasSyntaxErrors(), "Organized mixed layer output is invalid GAML");
		} finally {
			domain.dispose();
		}
	}

	private static void layerBoundaries(final IParser parser) {
		final var first = DisplaySource.parseLayer("species people; // end-of-line comment", "first", parser);
		final var second = DisplaySource.parseLayer("species people;", "second", parser);
		final var display = GamaFactory.eINSTANCE.createEDisplay();
		display.setName("boundaries");
		final var resource = new XMIResourceImpl(URI.createURI("memory:/boundaries.gadl"));
		resource.getContents().add(display);
		final var draft = new DisplayDraft(display.getName(), "", List.of(), List.of(first, second), "");
		DisplayWizardAdapter.apply(display, draft);
		check(DisplaySource.parseBody(ModelGenerator.displayBody(display), parser).layers().size() == 2,
				"An end-of-line comment swallowed the next layer");
		for (final String trailing : List.of("", "// final body comment")) {
			final var boundary = new DisplayDraft("boundary", "", List.of(), List.of(first), trailing);
			final String source = DisplaySource.renderDisplay(boundary, parser);
			check(!parser.parse(new StringReader("model boundary experiment run type:gui {output {" + source + "}}"))
					.hasSyntaxErrors(), "A final comment swallowed the display closing brace");
		}
		final var crlf = DisplaySource.parseBody("\r\nspecies people;\r\n// last comment\r\n", parser);
		final var windows = new DisplayDraft("windows", "", List.of(), crlf.layers(), crlf.trailingSource());
		final var windowsDisplay = GamaFactory.eINSTANCE.createEDisplay();
		windowsDisplay.setName("windows");
		resource.getContents().add(windowsDisplay);
		DisplayWizardAdapter.apply(windowsDisplay, windows);
		check(ModelGenerator.displayBody(windowsDisplay).equals("\r\nspecies people;\r\n// last comment\r\n"),
				"Applying CRLF layers normalized their line endings");
	}

	private static void legacyLayers(final IParser parser) {
		final var factory = GamaFactory.eINSTANCE;
		final var display = factory.createEDisplay();
		display.setName("legacy");
		final var text = factory.createELayer();
		text.setType("text");
		text.setName("caption");
		text.setText("A 'quoted' \"label\"");
		text.setSize("");
		text.setDisplay(display);
		final var chart = factory.createELayer();
		chart.setType("chart");
		chart.setName("History");
		chart.setChart_type("series");
		chart.setDisplay(display);
		final var data = factory.createEChartLayer();
		data.setName("N");
		data.setStyle("line");
		data.setValue("cycle");
		chart.getChartlayers().add(data);
		display.getLayers().addAll(List.of(text, chart));
		final var resource = new XMIResourceImpl(URI.createURI("memory:/legacy-displays.gadl"));
		resource.getContents().addAll(List.of(display, text, chart, data));
		final var initial = DisplayWizardAdapter.snapshot(display);
		check(initial.layers().stream().map(LayerDraft::kind).toList().equals(List.of(LayerKind.TEXT, LayerKind.RAW)),
				"Legacy text/chart layer classification failed");
		final String original = ModelGenerator.displayBody(display);
		final String originalChart = ModelGenerator.defineLayer(chart);
		final var domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		try {
			domain.getResourceSet().getResources().add(resource);
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { DisplayWizardAdapter.apply(display, initial); }
			});
			check(text.getGamlCode() == null && chart.getGamlCode() == null, "No-op migrated legacy fields");
			final var row = initial.layers().get(0);
			final var editedRow = new LayerDraft(row.id(), row.kind(), "\"Edited label\"", row.facets(), row.textFacets(),
					row.source(), row.body());
			final var edited = layers(initial, List.of(editedRow, initial.layers().get(1)));
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { DisplayWizardAdapter.apply(display, edited); }
			});
			check(display.getLayers().equals(List.of(text, chart)) && chart.getChartlayers().get(0) == data,
					"Editing legacy text replaced adjacent chart or its data");
			check(ModelGenerator.defineLayer(chart).strip().equals(originalChart.strip()),
					"Editing adjacent layer changed chart source");
			check(text.getGamlCode().contains("Edited label") && text.getText().equals("A 'quoted' \"label\""),
					"Source-backed edit lost original legacy field data");
			check(!parser.parse(new StringReader("model legacy experiment run type:gui {output {display legacy {"
					+ ModelGenerator.displayBody(display) + "}}}")).hasSyntaxErrors(), "Edited legacy text does not parse");
			final var materialized = DisplayWizardAdapter.snapshot(display);
			final var withoutChart = layers(materialized, List.of(materialized.layers().get(0)));
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { DisplayWizardAdapter.apply(display, withoutChart); }
			});
			check(chart.eResource() == null && data.eResource() == null, "Removing legacy chart left data orphan");
			domain.getCommandStack().undo();
			check(display.getLayers().contains(chart) && chart.getChartlayers().contains(data)
					&& data.eResource() == resource, "Chart removal undo lost data");
			domain.getCommandStack().undo();
			check(original.equals(ModelGenerator.displayBody(display)) && text.getGamlCode() == null, "Legacy text undo incomplete");
		} finally {
			domain.dispose();
		}
	}
}
