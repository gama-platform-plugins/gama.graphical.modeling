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
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.editor.ModelStructure;
import gama.ui.diagram.metamodel.EReflex;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.metamodel.GamaFactory;
import gama.ui.diagram.swt.image.GamaImageProvider;
import gaml.compiler.GamlStandaloneSetup;
import gaml.compiler.ui.wizards.behavior.BehaviorDraft;
import gaml.compiler.ui.wizards.behavior.BehaviorKind;

public final class LifecycleBehaviorTest {
	private static int checks;

	private static void check(final boolean value, final String message) {
		checks++;
		if (!value) throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		final var parser = new GamlStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
		final var model = DescriptionDiagramImporterTest.importSource(parser, """
				model lifecycles
				global {
					int counter <- 0;
					init { counter <- 1; }
					init startup when:true { counter <- 2; }
					init { counter <- 3; }
					abort cleanup when:true { write "first abort"; }
					abort { write "second abort"; }
					abort finish { write "third abort"; }
					reflex tick when:true { counter <- counter + 1; }
				}
				species conditional {
					init when:false { write "conditional init"; }
					init { write "following init"; }
				}
				species worker {
					init startup { write "first init"; }
					init { write "second init"; }
					init init { write "third init"; }
					abort { write "worker abort"; }
				}
				experiment run type:gui {
					init { write "experiment init"; }
					abort cleanup { write "experiment abort"; }
				}
				""");
		final var diagram = Graphiti.getPeCreateService().createDiagram("gama", "lifecycles", true);
		final var resource = new XMIResourceImpl(URI.createURI("memory:/lifecycles.gadl"));
		resource.getContents().addAll(List.of(diagram, model));
		final var world = (EWorldAgent) model.getObjects().stream().filter(EWorldAgent.class::isInstance).findFirst().orElseThrow();
		final var worker = (ESpecies) model.getObjects().stream().filter(o -> "worker".equals(o.getName())).findFirst().orElseThrow();
		final var conditional = (ESpecies) model.getObjects().stream().filter(o -> "conditional".equals(o.getName())).findFirst().orElseThrow();
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
		if (args.length > 0) java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]), original);
		check(!parser.parse(new StringReader(original)).hasSyntaxErrors(), "Imported lifecycle GAML is invalid");
		check(world.getInit().contains("counter <- 1;"), "Primary anonymous init lost");
		check(worker.getInit() == null || worker.getInit().isBlank(), "Later anonymous init moved ahead of named init");
		check(conditional.getInit() == null || conditional.getInit().isBlank(), "Conditional init moved into legacy storage");
		check(original.indexOf("conditional init") < original.indexOf("following init"), "Conditional init reordered");
		check(original.indexOf("counter <- 1;") < original.indexOf("counter <- 2;")
				&& original.indexOf("counter <- 2;") < original.indexOf("counter <- 3;"), "Global init order changed");
		check(original.indexOf("first init") < original.indexOf("second init")
				&& original.indexOf("second init") < original.indexOf("third init"), "Named/anonymous init order changed");
		check(original.indexOf("first abort") < original.indexOf("second abort"), "Abort order changed");
		check(original.indexOf("counter <- 1;") == original.lastIndexOf("counter <- 1;"), "Legacy init duplicated");
		check(SpeciesWizardAdapter.snapshot(world).reflexOrder().equals(List.of("tick")), "Lifecycle blocks appear in reflex order");
		check("reflex".equals(GamaFactory.eINSTANCE.createEReflex().getKind()), "Legacy reflex default changed");
		final var nodes = model.getObjects().stream().filter(EReflex.class::isInstance).map(EReflex.class::cast).toList();
		check(nodes.size() == 13, "Lifecycle import omitted or duplicated nodes: " + nodes.size());
		final var startup = nodes.stream().filter(n -> "startup".equals(n.getName()) && "init".equals(n.getKind()))
				.filter(n -> n.getReflexLinks().get(0).getSpecies() == world).findFirst().orElseThrow();
		final String initId = ModelStructure.getElementId(startup);
		final String marked = ModelGenerator.generateModel(fp, diagram, initId);
		check(marked.indexOf(initId) != marked.lastIndexOf(initId)
				&& marked.indexOf(initId, marked.indexOf(initId) + initId.length()) == marked.lastIndexOf(initId),
				"Snippet markers collide across lifecycle keywords");
		check(BehaviorWizardAdapter.validate(fp, diagram, null, world,
				new BehaviorDraft(BehaviorKind.INIT, "startup", List.of(), "")).contains("already exists"),
				"Same-kind duplicate name not refused");
		check(BehaviorWizardAdapter.validate(fp, diagram, null, world,
				new BehaviorDraft(BehaviorKind.ABORT, "startup", List.of(), "")).contains("already exists"),
				"Cross-kind duplicate name not refused");
		check(BehaviorWizardAdapter.validate(fp, diagram, null, worker,
				new BehaviorDraft(BehaviorKind.STATE, "startup", List.of(), "")).contains("already exists"),
				"Lifecycle/state duplicate name not refused");
		for (final var node : nodes) {
			final var initial = BehaviorWizardAdapter.snapshot(node);
			check(initial.kind().keyword().equals(node.getKind()), "Snapshot lost lifecycle kind");
			check(original.equals(BehaviorWizardAdapter.candidateModel(fp, diagram, node, null, initial)), "No-op changed lifecycle source");
			final var context = BehaviorWizardAdapter.bodyContext(fp, diagram, node, null, initial);
			check(original.equals(context.prefix() + initial.body() + context.suffix()), "Body context changed lifecycle source");
			final String expectedIcon = switch (node.getKind()) {
				case "init" -> GamaImageProvider.IMG_INIT;
				case "abort" -> GamaImageProvider.IMG_ABORT;
				default -> GamaImageProvider.IMG_REFLEXLINK;
			};
			check((expectedIcon + ".dark").equals(GamaImageProvider.nodeImageId(node)), "Wrong lifecycle icon");
		}
		final var domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		try {
			domain.getResourceSet().getResources().add(resource);
			final var tick = nodes.stream().filter(n -> "tick".equals(n.getName())).findFirst().orElseThrow();
			final var initial = BehaviorWizardAdapter.snapshot(tick);
			final var relation = tick.getReflexLinks().get(0);
			final var facet = tick.getFacets().get(0);
			for (final var kind : List.of(BehaviorKind.INIT, BehaviorKind.ABORT, BehaviorKind.REFLEX)) {
				final var draft = new BehaviorDraft(kind, initial.name(), initial.facets(), initial.body());
				final String candidate = BehaviorWizardAdapter.candidateModel(fp, diagram, tick, null, draft);
				check(!parser.parse(new StringReader(candidate)).hasSyntaxErrors(), "Conversion produces invalid GAML");
				check(initial.equals(BehaviorWizardAdapter.snapshot(tick)), "Conversion preview mutated live node");
				domain.getCommandStack().execute(new RecordingCommand(domain) {
					@Override protected void doExecute() { BehaviorWizardAdapter.apply(tick, draft); }
				});
				check(draft.equals(BehaviorWizardAdapter.snapshot(tick)), "Conversion lost name/facets/body");
				check(tick.getReflexLinks().get(0) == relation && tick.getFacets().get(0) == facet,
						"Conversion replaced linked identities");
				check(world.getInit().contains("counter <- 1;"), "Conversion overwrote primary init");
				check(SpeciesWizardAdapter.snapshot(world).reflexOrder().contains("tick") == (kind == BehaviorKind.REFLEX),
						"Reflex order includes an inactive lifecycle node");
				domain.getCommandStack().undo();
				check(initial.equals(BehaviorWizardAdapter.snapshot(tick))
						&& original.equals(ModelGenerator.generateModel(fp, diagram, null)), "Conversion undo incomplete");
				domain.getCommandStack().redo();
				check(draft.equals(BehaviorWizardAdapter.snapshot(tick)), "Conversion redo incomplete");
				domain.getCommandStack().undo();
				final var fresh = new BehaviorDraft(kind, "", initial.facets(), "write \"created\";");
				final String creation = BehaviorWizardAdapter.candidateModel(fp, diagram, null, worker, fresh);
				check(creation.contains("write \"created\";") && !parser.parse(new StringReader(creation)).hasSyntaxErrors(),
						"Anonymous lifecycle creation failed");
			}
			final var bytes = new java.io.ByteArrayOutputStream();
			resource.save(bytes, Map.of());
			final String serialized = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
			check(!serialized.contains("kind=\"reflex\""), "Legacy default should not need a persisted kind");
			final var loaded = new XMIResourceImpl();
			loaded.load(new java.io.ByteArrayInputStream(bytes.toByteArray()), Map.of());
			final var contents = loaded.getAllContents();
			int restored = 0;
			while (contents.hasNext()) if (contents.next() instanceof EReflex saved) {
				check(nodes.stream().anyMatch(node -> BehaviorWizardAdapter.snapshot(node)
						.equals(BehaviorWizardAdapter.snapshot(saved))), "XMI lost lifecycle kind/body/facets");
				restored++;
			}
			check(restored == nodes.size(), "XMI lost lifecycle nodes");
			final var legacy = GamaFactory.eINSTANCE.createEReflex();
			legacy.setKind("init");
			legacy.eUnset(gama.ui.diagram.metamodel.GamaPackage.Literals.EREFLEX__KIND);
			check("reflex".equals(legacy.getKind())
					&& !legacy.eIsSet(gama.ui.diagram.metamodel.GamaPackage.Literals.EREFLEX__KIND),
					"Reflective unset did not restore the legacy default");
		} finally {
			domain.dispose();
		}
		System.out.println("PASS: " + checks + " lifecycle import, ordering, conversion, context, identity, undo and persistence checks");
	}
}
