package gama.ui.diagram.swt.editFrame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.PictogramsFactory;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.xtext.parser.IParser;

import gama.api.compilation.descriptions.IModelDescription;
import gama.ui.diagram.features.modelgeneration.DescriptionDiagramImporterTest;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EAction;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EWorldAgent;
import gaml.compiler.GamlStandaloneSetup;
import gaml.compiler.resource.GamlResource;
import gaml.compiler.resource.GamlResourceServices;
import gaml.compiler.ui.wizards.action.ActionDraft;
import gaml.compiler.ui.wizards.action.ActionDraft.Argument;

/** Real parser/EMF preview and transaction checks; compiler lifecycle uses an isolated description double. */
public final class ActionWizardAdapterTest {
	private static int checks;

	private static void check(final boolean condition, final String message) {
		checks++;
		if (!condition) throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		SpeciesWizardAdapterTest.initializeWorkspace();
		final var parser = new GamlStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
		final var imported = DescriptionDiagramImporterTest.importSource(parser, """
				model actions
				global {
					int global_value <- 5;
					int calculate(int amount <- 2) { return global_value * amount; }
				}
				species worker {
					int energy <- 4;
					int calculate(int amount <- 3) { return energy + amount; }
				}
				class base virtual:true {
					int value <- 1;
					float read() virtual:true;
					int calculate(int amount <- 1) { return value + amount; }
				}
				grid cells width:2 height:2 { action reset() { write "cell"; } }
				experiment run type:gui { action reset() { write "experiment"; } }
				""");
		final var diagram = Graphiti.getPeCreateService().createDiagram("gama", "actions", true);
		final var resource = new XMIResourceImpl(URI.createURI("memory:/actions.gadl"));
		resource.getContents().addAll(List.of(diagram, imported));
		for (final var object : imported.getObjects()) {
			if (!(object instanceof ESpecies)) continue;
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
		final var actions = imported.getObjects().stream().filter(EAction.class::isInstance).map(EAction.class::cast).toList();
		final String original = ModelGenerator.generateModel(fp, diagram, null);
		parse(parser, original);
		for (final EAction action : actions) {
			final var initial = ActionWizardAdapter.snapshot(action);
			check(original.equals(ActionWizardAdapter.candidateModel(fp, diagram, action, null, initial)),
					"Unchanged action preview changed the model: " + action.getName());
			check(ActionWizardAdapter.owner(action) != null, "Action has no contextual owner");
			if (!initial.virtual()) {
				final var context = ActionWizardAdapter.bodyContext(fp, diagram, action, null, initial);
				parse(parser, context.prefix() + initial.body() + context.suffix());
				check((context.prefix() + initial.body() + context.suffix()).equals(original),
						"Embedded body context changed surrounding source");
			}
		}
		final var world = (EWorldAgent) imported.getObjects().stream().filter(EWorldAgent.class::isInstance).findFirst().orElseThrow();
		final var worker = (ESpecies) imported.getObjects().stream().filter(o -> "worker".equals(o.getName())).findFirst().orElseThrow();
		final var base = (ESpecies) imported.getObjects().stream().filter(o -> "base".equals(o.getName())).findFirst().orElseThrow();
		final var action = worker.getActionLinks().get(0).getAction();
		final var initial = ActionWizardAdapter.snapshot(action);
		final var argument = action.getVariables().get(0);
		final var actionLink = action.getActionLinks().get(0);
		final var changed = new ActionDraft("compute", "int", false,
				List.of(new Argument("amount", "int", "8"), new Argument("factor", "int", "2")),
				"// braces { } remain in code\nreturn energy + amount * factor;");
		final String candidate = ActionWizardAdapter.candidateModel(fp, diagram, action, null, changed);
		parse(parser, candidate);
		check(candidate.contains("int compute(int amount <- 8, int factor <- 2)"), "Signature/default/order missing");
		check(candidate.contains("return energy + amount * factor;") && candidate.contains("int global_value"),
				"Context/body missing in preview");
		check(initial.equals(ActionWizardAdapter.snapshot(action)), "Preview changed live action");
		final var virtual = new ActionDraft("compute", "int", true, changed.arguments(), changed.body());
		final String abstractSource = ActionWizardAdapter.candidateModel(fp, diagram, action, null, virtual);
		parse(parser, abstractSource);
		check(abstractSource.contains("int compute(int amount <- 8, int factor <- 2) virtual:true;")
				&& !abstractSource.contains("return energy + amount * factor"), "Virtual action has executable body");
		final var inactive = new ActionDraft("compute", "int", true, changed.arguments(), "not valid GAML {{{");
		final String inactiveSource = ActionWizardAdapter.candidateModel(fp, diagram, action, null, inactive);
		parse(parser, inactiveSource);
		check(!inactiveSource.contains(inactive.body()), "Inactive virtual body leaked into generated model");
		final var badDefault = new ActionDraft("compute", "int", false,
				List.of(new Argument("amount", "int", "1 +")), "return amount;");
		check(parser.parse(new java.io.StringReader(
				ActionWizardAdapter.candidateModel(fp, diagram, action, null, badDefault))).hasSyntaxErrors(),
				"Malformed default argument was accepted as valid GAML");
		final var fresh = new ActionDraft("new_action", "action", false, List.of(), "write \"new\";");
		final int roots = resource.getContents().size();
		for (final ESpecies owner : List.of(world, worker, base)) {
			final var created = ActionWizardAdapter.candidateModel(fp, diagram, null, owner, fresh);
			parse(parser, created);
			check(created.contains("action new_action()"), "Creation preview absent");
		}
		check(resource.getContents().size() == roots && original.equals(ModelGenerator.generateModel(fp, diagram, null)),
				"Cancelled creation left objects or links");
		check(ActionWizardAdapter.typeNames(diagram).containsAll(List.of("action", "worker", "base", "cells")),
				"Return types omit action or model-defined types");
		check(!ActionWizardAdapter.typeNames(diagram).contains("run"), "Experiment offered as a return type");
		final var virtualFacet = gama.ui.diagram.metamodel.GamaFactory.eINSTANCE.createEFacet();
		virtualFacet.setName("virtual");
		virtualFacet.setValue(" false ");
		action.getFacets().add(virtualFacet);
		final var documentation = gama.ui.diagram.metamodel.GamaFactory.eINSTANCE.createEFacet();
		documentation.setName("doc");
		documentation.setValue("\"Keep this documentation\"");
		action.getFacets().add(documentation);
		final var domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		try {
			domain.getResourceSet().getResources().add(resource);
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { ActionWizardAdapter.apply(action, changed); }
			});
			check(changed.equals(ActionWizardAdapter.snapshot(action)), "Edit did not round-trip");
			parse(parser, ModelGenerator.generateModel(fp, diagram, null));
			check(action.getVariables().get(0) == argument && action.getActionLinks().get(0) == actionLink,
					"Edit replaced existing argument/action relation");
			check(action.getFacets().get(0) == virtualFacet && " false ".equals(virtualFacet.getValue())
					&& action.getFacets().get(1) == documentation
					&& "\"Keep this documentation\"".equals(documentation.getValue()),
					"Unchanged virtual expression or unrelated facet lost its identity/value");
			domain.getCommandStack().undo();
			check(initial.equals(ActionWizardAdapter.snapshot(action)) && action.getVariables().get(0) == argument,
					"Undo lost values or identities");
			domain.getCommandStack().redo();
			check(changed.equals(ActionWizardAdapter.snapshot(action)), "Redo lost edit");
			final var factor = action.getVariables().get(1);
			final var reordered = new ActionDraft(changed.name(), changed.returnType(), false,
					List.of(changed.arguments().get(1), changed.arguments().get(0)), changed.body());
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { ActionWizardAdapter.apply(action, reordered); }
			});
			check(action.getVariables().get(0) == factor && action.getVariables().get(1) == argument,
					"Reordering replaced arguments");
			domain.getCommandStack().undo();
			check(changed.equals(ActionWizardAdapter.snapshot(action)) && action.getVariables().get(0) == argument,
					"Reorder undo lost arguments or order");
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() { ActionWizardAdapter.apply(action, virtual); }
			});
			check(ActionWizardAdapter.snapshot(action).virtual() && changed.body().equals(action.getGamlCode()),
					"Virtual toggle lost inactive code");
			final var bytes = new ByteArrayOutputStream();
			resource.save(bytes, Map.of());
			final var reload = new XMIResourceImpl(URI.createURI("memory:/reloaded.gadl"));
			reload.load(new ByteArrayInputStream(bytes.toByteArray()), Map.of());
			final var contents = reload.getAllContents();
			EAction saved = null;
			while (contents.hasNext()) {
				if (contents.next() instanceof EAction a && "compute".equals(a.getName())) saved = a;
			}
			check(saved != null && virtual.equals(ActionWizardAdapter.snapshot(saved)), "Action XMI lost draft state");
			final var copy = EcoreUtil.copy(action);
			check(ActionWizardAdapter.snapshot(copy).equals(virtual), "Action copy lost body/arguments/virtual");
			final var added = gama.ui.diagram.metamodel.GamaFactory.eINSTANCE.createEAction();
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override protected void doExecute() {
					ActionWizardAdapter.attach(base, added);
					ActionWizardAdapter.apply(added, fresh);
				}
			});
			check(ActionWizardAdapter.owner(added) == base && base.getActionLinks().size() == 3, "Creation not linked to class");
			domain.getCommandStack().undo();
			check(added.eResource() == null && base.getActionLinks().size() == 2, "Creation undo left objects/links");
			domain.getCommandStack().redo();
			check(added.eResource() == resource && base.getActionLinks().size() == 3, "Creation redo lost objects/links");
		} finally {
			domain.dispose();
		}
		checkValidation(fp, diagram, action, virtual);
		System.out.println("PASS: " + checks + " action context, signature/default/virtual, preview, persistence, identity and undo checks");
	}

	private static void parse(final IParser parser, final String source) {
		final var result = parser.parse(new java.io.StringReader(source));
		if (result.hasSyntaxErrors()) {
			throw new AssertionError(result.getSyntaxErrors().iterator().next().getSyntaxErrorMessage().getMessage() + "\n" + source);
		}
		checks++;
	}

	private static void checkValidation(final IFeatureProvider fp, final org.eclipse.graphiti.mm.pictograms.Diagram diagram,
			final EAction action, final ActionDraft draft) throws ReflectiveOperationException {
		final var factories = Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap();
		final Object previous = factories.get("gaml");
		final boolean[] disposed = { false }, unloaded = { false }, fail = { false };
		final int[] toolbar = { 0 };
		final boolean[] importsCleared = { false };
		final URI fullURI = diagram.eResource().getURI().trimFileExtension().appendFileExtension("gaml");
		final var indexField = gaml.compiler.indexer.GamlResourceIndexer.class.getDeclaredField("index");
		indexField.setAccessible(true);
		final var index = (gaml.compiler.indexer.GamlResourceGraph) indexField.get(null);
		index.addEdge(ActionWizardAdapter.previewURI(diagram), fullURI, null);
		GamlResourceServices.addResourceListener(fullURI, (model, experiments, context) -> toolbar[0]++);
		factories.put("gaml", (Resource.Factory) uri -> {
			final var resource = new GamlResource() {
				@Override public boolean hasErrors() { return fail[0]; }
				@Override public void reparse(final String source) {
					check("model diagram_wizard_disposed".equals(source), "Unexpected import cleanup source");
					getContents().clear();
					getContents().add(gaml.compiler.gaml.GamlFactory.eINSTANCE.createModel());
					importsCleared[0] = true;
				}
				@Override protected void doLoad(final InputStream stream, final Map<?, ?> options) throws IOException {
					check(getURI().equals(ActionWizardAdapter.previewURI(diagram)), "Action preview URI not isolated");
					check(new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("virtual:true;"),
							"Wrong candidate validated");
					if (fail[0]) getErrors().add(new Resource.Diagnostic() {
						@Override public String getMessage() { return "Invalid action"; }
						@Override public String getLocation() { return uri.toString(); }
						@Override public int getLine() { return 1; }
						@Override public int getColumn() { return 1; }
					});
				}
				@Override public IModelDescription buildCompleteDescription() {
					return (IModelDescription) Proxy.newProxyInstance(IModelDescription.class.getClassLoader(),
							new Class<?>[] { IModelDescription.class }, (proxy, method, args) -> switch (method.getName()) {
								case "validate" -> proxy;
								case "dispose" -> { disposed[0] = true; yield null; }
								default -> throw new AssertionError(method.getName());
							});
				}
				@Override protected void doUnload() { unloaded[0] = true; }
			};
			resource.setURI(uri);
			return resource;
		});
		try {
			check(ActionWizardAdapter.validate(fp, diagram, action, null, draft) == null, "Valid action rejected");
			check(disposed[0] && unloaded[0] && toolbar[0] == 0, "Validation leaked resource or changed toolbar");
			check(importsCleared[0] && gaml.compiler.indexer.GamlResourceIndexer
					.directImportsOf(ActionWizardAdapter.previewURI(diagram)).isEmpty(),
					"Preview import edges survived disposal");
			fail[0] = true;
			check("Invalid action".equals(ActionWizardAdapter.validate(fp, diagram, action, null, draft)), "Action error swallowed");
			final var duplicate = new ActionDraft(draft.name(), "int", false,
					List.of(new Argument("a", "int", ""), new Argument("a", "float", "")), "");
			check(ActionWizardAdapter.validate(fp, diagram, action, null, duplicate) != null, "Duplicate args accepted");
		} finally {
			if (previous == null) factories.remove("gaml"); else factories.put("gaml", previous);
			GamlResourceServices.removeResourceListener(fullURI);
		}
	}
}
