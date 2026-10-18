package gama.ui.diagram.swt.editFrame;

import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
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
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EWorldAgent;
import gaml.compiler.GamlStandaloneSetup;
import gaml.compiler.ui.wizards.behavior.BehaviorDraft;
import gaml.compiler.ui.wizards.behavior.BehaviorKind;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Facet;

public final class BehaviorWizardAdapterTest {
	private static int checks;

	private static void check(final boolean value, final String message) {
		checks++;
		if (!value) throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		final var parser = new GamlStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
		final var model = DescriptionDiagramImporterTest.importSource(parser, """
				model behaviors
				global {
					int counter <- 0;
					reflex first when:true { counter <- counter + 1; }
					reflex when:false { write "anonymous one"; }
					reflex when:false { write "anonymous two"; }
					reflex when:false { write "anonymous three"; }
				}
				species worker control:fsm {
					init startup when:true { write "startup"; }
					abort cleanup when:true { write "cleanup"; }
					state rest initial:true { write "rest"; }
					state finished final:true;
					task choose weight:1 { write "choose"; }
					plan proceed when:true { write "proceed"; }
					perceive observe target:[] { write "observe"; }
					rule react when:true;
				}
				""");
		final var diagram = Graphiti.getPeCreateService().createDiagram("gama", "behaviors", true);
		final var resource = new XMIResourceImpl(URI.createURI("memory:/behaviors.gadl"));
		resource.getContents().addAll(List.of(diagram, model));
		for (final var object : model.getObjects()) if (object instanceof EWorldAgent) {
			final var shape = Graphiti.getPeCreateService().createContainerShape(diagram, true);
			final var link = PictogramsFactory.eINSTANCE.createPictogramLink();
			link.getBusinessObjects().add(object);
			shape.setLink(link);
			diagram.getPictogramLinks().add(link);
		}
		final var fp = (IFeatureProvider) Proxy.newProxyInstance(IFeatureProvider.class.getClassLoader(),
				new Class<?>[] { IFeatureProvider.class }, (proxy, method, arguments) -> {
					if ("getBusinessObjectForPictogramElement".equals(method.getName())) {
						return Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement((PictogramElement) arguments[0]);
					}
					throw new UnsupportedOperationException(method.getName());
				});
		final var world = (EWorldAgent) model.getObjects().stream().filter(EWorldAgent.class::isInstance).findFirst().orElseThrow();
		final var worker = (ESpecies) model.getObjects().stream().filter(o -> "worker".equals(o.getName())).findFirst().orElseThrow();
		final String original = ModelGenerator.generateModel(fp, diagram, null);
		check(!parser.parse(new StringReader(original)).hasSyntaxErrors(), "Imported behaviors generate invalid GAML");
		check(original.contains("anonymous one") && original.contains("anonymous two"), "Anonymous reflexes collapsed");
		check(worker.getStateLinks().size() == 2 && original.contains("state finished"), "Bodyless state lost");
		final var noSource = new org.eclipse.graphiti.features.context.impl.CreateConnectionContext();
		for (final var tool : List.of(
				new gama.ui.diagram.features.create.CreateReflexLinkFeature(fp),
				new gama.ui.diagram.features.create.CreateStateLinkFeature(fp),
				new gama.ui.diagram.features.create.CreateTaskLinkFeature(fp),
				new gama.ui.diagram.features.create.CreatePlanLinkFeature(fp),
				new gama.ui.diagram.features.create.CreatePerceiveLinkFeature(fp),
				new gama.ui.diagram.features.create.CreateRuleLinkFeature(fp))) {
			check(tool.create(noSource) == null && !tool.hasDoneChanges(), "Ineligible behavior creation reports a change");
		}
		final var domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		try {
			domain.getResourceSet().getResources().add(resource);
			final var lastAnonymous = world.getReflexLinks().get(3).getReflex();
			final var anonymousDraft = BehaviorWizardAdapter.snapshot(lastAnonymous);
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() {
					BehaviorWizardAdapter.apply(lastAnonymous, new BehaviorDraft(anonymousDraft.kind(),
							"last_anonymous", anonymousDraft.facets(), anonymousDraft.body()));
				}
			});
			final String renamedAnonymous = ModelGenerator.generateModel(fp, diagram, null);
			check(renamedAnonymous.indexOf("anonymous one") < renamedAnonymous.indexOf("anonymous two"),
					"Renaming a later anonymous reflex changed execution order");
			check(renamedAnonymous.indexOf("anonymous two") < renamedAnonymous.indexOf("anonymous three"),
					"Multiple remaining anonymous reflexes changed execution order");
			domain.getCommandStack().undo();
			for (final var kind : DiagramBehavior.values()) {
				final EGamaObject object = kind.links(kind == DiagramBehavior.REFLEX ? world : worker).stream()
						.map(link -> link.getTarget()).filter(target -> DiagramBehavior.of(target) == kind).findFirst().orElseThrow();
				final var initial = BehaviorWizardAdapter.snapshot(object);
				final var relation = kind.links(object).get(0);
				final var facet = object.getFacets().get(0);
				check(initial.kind() == BehaviorKind.of(kind.keyword()), "Wrong behavior kind");
				check(original.equals(BehaviorWizardAdapter.candidateModel(fp, diagram, object, null, initial)),
						"No-op preview changed behavior source");
				final var context = BehaviorWizardAdapter.bodyContext(fp, diagram, object, null, initial);
				check(original.equals(context.prefix() + (kind == DiagramBehavior.RULE ? "" : initial.body())
						+ context.suffix()), "Body context changed source");
				final var changed = new BehaviorDraft(initial.kind(), "changed_" + kind.keyword(), initial.facets(),
						"// { braces }\nwrite \"new body\";");
				final String candidate = BehaviorWizardAdapter.candidateModel(fp, diagram, object, null, changed);
				if (kind == DiagramBehavior.RULE) {
					check(!candidate.contains("new body") && context.suffix().isEmpty(), "Rule rendered inactive body");
				}
				check(!parser.parse(new StringReader(candidate)).hasSyntaxErrors(), "Behavior edit has invalid syntax");
				check(initial.equals(BehaviorWizardAdapter.snapshot(object)), "Preview changed live behavior");
				domain.getCommandStack().execute(new RecordingCommand(domain) {
					@Override protected void doExecute() { BehaviorWizardAdapter.apply(object, changed); }
				});
				check(changed.equals(BehaviorWizardAdapter.snapshot(object)), "Edit did not round-trip");
				check(kind.links(object).get(0) == relation && object.getFacets().get(0) == facet,
						"Edit replaced link/facet identity");
				if (kind == DiagramBehavior.REFLEX) {
					check(ModelGenerator.orderedReflexes(world).stream()
							.filter(link -> "reflex".equals(link.getReflex().getKind())).findFirst().orElseThrow()
							.getReflex() == object, "Reflex rename changed execution order");
				}
				domain.getCommandStack().undo();
				check(initial.equals(BehaviorWizardAdapter.snapshot(object)), "Undo lost original behavior");
				domain.getCommandStack().redo();
				check(changed.equals(BehaviorWizardAdapter.snapshot(object)), "Redo lost edited behavior");
				domain.getCommandStack().undo();
				final var owner = kind.owner(object);
				final int count = kind.links(owner).size();
				final var fresh = new BehaviorDraft(initial.kind(), "created_" + kind.keyword(), initial.facets(), "write 1;");
				final String creation = BehaviorWizardAdapter.candidateModel(fp, diagram, null, owner, fresh);
				check(creation.contains("created_" + kind.keyword()) && kind.links(owner).size() == count,
						"Creation preview mutated live graph");
				final var created = kind.create();
				domain.getCommandStack().execute(new RecordingCommand(domain) {
					@Override protected void doExecute() {
						BehaviorWizardAdapter.apply(created, fresh);
						kind.attach(owner, created);
					}
				});
				check(kind.owner(created) == owner && created.getModel() == model, "Creation lost model/owner");
				domain.getCommandStack().undo();
				check(created.eResource() == null && kind.links(owner).size() == count, "Creation undo left graph objects");
			}
			final var bytes = new java.io.ByteArrayOutputStream();
			resource.save(bytes, java.util.Map.of());
			final var loaded = new XMIResourceImpl();
			loaded.load(new java.io.ByteArrayInputStream(bytes.toByteArray()), java.util.Map.of());
			final var copy = EcoreUtil.copy(model);
			check(copy.getObjects().size() == model.getObjects().size(), "Copy lost behavior nodes");
			check(loaded.getContents().size() == resource.getContents().size(), "XMI lost graph roots");
			check(original.equals(ModelGenerator.generateModel(fp, diagram, null)), "Undo failed to restore generated source");
		} finally {
			domain.dispose();
		}
		System.out.println("PASS: " + checks + " behavior drafts, ownership, context, previews, identities, creation and undo checks");
	}
}
