package gama.ui.diagram.swt.editFrame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.impl.AddContext;
import org.eclipse.graphiti.features.context.impl.CreateConnectionContext;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.PictogramsFactory;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.xtext.parser.IParser;

import gama.ui.diagram.features.add.AddSpeciesFeature;
import gama.ui.diagram.features.create.CreateActionLinkFeature;
import gama.ui.diagram.features.create.CreateClassInheritingLinkFeature;
import gama.ui.diagram.features.create.CreateClassLinkFeature;
import gama.ui.diagram.features.create.CreateInheritingLinkFeature;
import gama.ui.diagram.features.create.CreateReflexLinkFeature;
import gama.ui.diagram.features.create.CreateSubGridLinkFeature;
import gama.ui.diagram.features.create.CreateSubSpeciesLinkFeature;
import gama.ui.diagram.features.modelgeneration.DescriptionDiagramImporterTest;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EClassSpecies;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.metamodel.GamaFactory;
import gama.ui.diagram.metamodel.GamaPackage;
import gaml.compiler.GamlStandaloneSetup;
import gaml.compiler.ui.wizards.species.SpeciesDraft;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Facet;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Variable;

/** Class import/persistence, detached edits, inheritance constraints and palette eligibility. */
public final class ClassDiagramTest {
	private static int checks;

	private static void check(final boolean value, final String message) {
		checks++;
		if (!value) throw new AssertionError(message);
	}

	private static Anchor box(final Diagram diagram, final EGamaObject object) {
		final var shape = Graphiti.getPeCreateService().createContainerShape(diagram, true);
		final var link = PictogramsFactory.eINSTANCE.createPictogramLink();
		link.getBusinessObjects().add(object);
		shape.setLink(link);
		diagram.getPictogramLinks().add(link);
		return Graphiti.getPeCreateService().createChopboxAnchor(shape);
	}

	private static SpeciesDraft draft(final String name, final String parent, final String init) {
		return new SpeciesDraft(name, List.of(), List.of(new Facet("parent", parent), new Facet("virtual", "false")),
				List.of(new Variable("value", "int", init, "", "", "", "")), "", List.of());
	}

	private static void rejected(final ESpecies species, final SpeciesDraft draft) {
		final var before = SpeciesWizardAdapter.snapshot(species);
		try {
			SpeciesWizardAdapter.apply(species, draft);
			throw new AssertionError("Invalid class draft accepted: " + draft);
		} catch (final IllegalArgumentException expected) {
			check(!expected.getMessage().isBlank(), "Missing rejection reason");
		}
		check(before.equals(SpeciesWizardAdapter.snapshot(species)), "Invalid draft partially applied");
	}

	public static void main(final String[] args) throws Exception {
		final var injector = new GamlStandaloneSetup().createInjectorAndDoEMFRegistration();
		final var parser = injector.getInstance(IParser.class);
		final String source = """
				model class_diagram
				global {}
				class base virtual: true {
					int value <- 1;
					int evaluate(int multiplier) { return value * multiplier; }
					float read() virtual: true;
					float implicit();
				}
				class child parent: base {
					string label <- "kept";
				}
				class other { int value <- 2; }
				species animal { int energy <- 3; }
				""";
		final var imported = DescriptionDiagramImporterTest.importSource(parser, source);
		final var world = (EWorldAgent) imported.getObjects().stream().filter(EWorldAgent.class::isInstance).findFirst().orElseThrow();
		final var base = (EClassSpecies) imported.getObjects().stream().filter(o -> "base".equals(o.getName())).findFirst().orElseThrow();
		final var child = (EClassSpecies) imported.getObjects().stream().filter(o -> "child".equals(o.getName())).findFirst().orElseThrow();
		final var other = (EClassSpecies) imported.getObjects().stream().filter(o -> "other".equals(o.getName())).findFirst().orElseThrow();
		final var animal = (ESpecies) imported.getObjects().stream().filter(o -> "animal".equals(o.getName())).findFirst().orElseThrow();
		check(child.getInheritsFrom() == base, "Class parent not imported");
		check(!CreateInheritingLinkFeature.canInherit(child, base)
				&& !CreateInheritingLinkFeature.canInherit(base, base), "Class inheritance allows self/indirect cycles");
		check(base.getMacroSpeciesLinks().get(0).getMacro() == world, "Class owner not world");
		check(base.getSkills().isEmpty() && base.getVariables().size() == 1, "Agent attributes leaked into class");
		check(base.getActionLinks().size() == 3 && base.getActionLinks().get(0).getAction().getGamlCode().contains("return value * multiplier"),
				"Class action body lost");
		check("int".equals(base.getActionLinks().get(0).getAction().getReturnType())
				&& base.getActionLinks().get(0).getAction().getVariables().size() == 1, "Class action signature lost");
		for (final var link : base.getActionLinks().subList(1, 3)) {
			check(link.getAction().getFacets().stream().anyMatch(f -> "virtual".equals(f.getName()) && "true".equals(f.getValue())),
					"Consumed/implicit virtual action flag lost");
		}
		check(GamaPackage.eINSTANCE.getEClassSpecies().getClassifierID() == 39, "Classifier IDs shifted");
		final var resource = new XMIResourceImpl(URI.createURI("memory:/classes.gadl"));
		final Diagram diagram = Graphiti.getPeCreateService().createDiagram("gama", "class_diagram", true);
		resource.getContents().add(diagram);
		resource.getContents().add(imported);
		final Anchor worldAnchor = box(diagram, world);
		final Anchor baseAnchor = box(diagram, base);
		final Anchor childAnchor = box(diagram, child);
		final Anchor animalAnchor = box(diagram, animal);
		box(diagram, other);
		final var fp = (IFeatureProvider) Proxy.newProxyInstance(IFeatureProvider.class.getClassLoader(),
				new Class<?>[] { IFeatureProvider.class }, (proxy, method, arguments) -> {
					if ("getBusinessObjectForPictogramElement".equals(method.getName())) {
						return Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement((PictogramElement) arguments[0]);
					}
					throw new UnsupportedOperationException(method.getName());
				});
		final String generated = ModelGenerator.generateModel(fp, diagram, null);
		check(generated.contains("class base virtual:true") && generated.contains("class child parent:base"),
				"Class keyword/facets lost: " + generated);
		check(!generated.substring(generated.indexOf("class base"), generated.indexOf("class child")).contains("init {"),
				"Class has invented init");
		check(!parser.parse(new StringReader(generated)).hasSyntaxErrors(), "Class generation does not parse");
		final var bytes = new ByteArrayOutputStream();
		resource.save(bytes, Map.of());
		final var loaded = new XMIResourceImpl(URI.createURI("memory:/reloaded.gadl"));
		loaded.load(new ByteArrayInputStream(bytes.toByteArray()), Map.of());
		check(loaded.getAllContents().hasNext() && bytes.toString().contains("EClassSpecies"), "Class XMI type absent");
		final var loadedClasses = new java.util.ArrayList<EClassSpecies>();
		final var contents = loaded.getAllContents();
		while (contents.hasNext()) if (contents.next() instanceof EClassSpecies c) loadedClasses.add(c);
		check(loadedClasses.size() == 3, "Class subtype lost on reload");
		check(loadedClasses.stream().filter(c -> "child".equals(c.getName())).findFirst().orElseThrow().getInheritsFrom()
				instanceof EClassSpecies, "Reload lost class inheritance");
		final var initial = SpeciesWizardAdapter.snapshot(child);
		check(generated.equals(SpeciesWizardAdapter.candidateModel(fp, diagram, child, null, initial)), "Unchanged class preview changed source");
		final var previewClass = GamaFactory.eINSTANCE.createEClassSpecies();
		final String preview = SpeciesWizardAdapter.candidateModel(fp, diagram, previewClass, world, draft("new_class", "base", "7"));
		check(preview.contains("class new_class parent:base") && !preview.contains("species new_class"), "Creation preview is not a class");
		check(world.getMicroSpeciesLinks().size() == 4 && previewClass.eResource() == null, "Creation preview mutated live model");
		final var parents = SpeciesWizardAdapter.parentNames(child, child);
		check(parents.containsAll(List.of("object", "base", "other")) && !parents.contains("agent") && !parents.contains("animal"),
				"Class parent choices contain species");
		check(!SpeciesWizardAdapter.parentNames(base, base).contains("child"), "Descendant offered as class parent");
		check(!SpeciesWizardAdapter.parentNames(animal, animal).contains("base"), "Species offered class parent");
		check(!SpeciesWizardAdapter.mirrorNames(animal).contains("base"), "Class offered as mirror species");
		final var inherited = SpeciesWizardAdapter.localAttributes(child, initial);
		check(inherited.size() == 1 && "value".equals(inherited.get(0).variable().name()), "Class inherited attributes missing");
		final var contextParents = SpeciesWizardAdapter.wizardContext(child).parentNames(List.of("object", "agent", "base", "child", "animal"));
		check(contextParents.contains("object") && !contextParents.contains("agent")
				&& !contextParents.contains("animal") && !contextParents.contains("child"), "Merged class context leaks parents");
		rejected(child, draft("child", "animal", "2"));
		rejected(animal, draft("animal", "base", "2"));
		rejected(animal, draft("animal", "object", "2"));
		rejected(base, draft("base", "child", "2"));
		rejected(child, new SpeciesDraft("child", List.of("moving"), initial.facets(), initial.variables(), "", List.of()));
		check(SpeciesWizardAdapter.validate(fp, diagram, previewClass, animal, draft("nested", "object", "1"))
				.contains("world"), "Nested class creation accepted");
		check(SpeciesWizardAdapter.validate(fp, diagram, previewClass, world, draft("animal", "object", "1"))
				.contains("already exists"), "Duplicate class name accepted");
		final var domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		try {
			domain.getResourceSet().getResources().add(resource);
			final var action = base.getActionLinks().get(0);
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override
				protected void doExecute() {
					SpeciesWizardAdapter.apply(child, draft("child", "other", "10"));
				}
			});
			check(child.getInheritsFrom() == other && "10".equals(child.getVariables().get(0).getInit()), "Class edit not applied");
			domain.getCommandStack().undo();
			check(initial.equals(SpeciesWizardAdapter.snapshot(child)) && child.getInheritsFrom() == base, "Class undo incomplete");
			domain.getCommandStack().redo();
			check(child.getInheritsFrom() == other && base.getActionLinks().get(0) == action, "Class redo/action identity lost");
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override
				protected void doExecute() {
					SpeciesWizardAdapter.apply(child, draft("child", "object", "11"));
				}
			});
			check(child.getInheritsFrom() == null && child.getFacets().stream().anyMatch(f -> "parent".equals(f.getName())
					&& "object".equals(f.getValue())), "Reset class parent did not retain object root");
		} finally {
			domain.dispose();
		}
		final var create = new CreateConnectionContext();
		final var classTool = new CreateClassLinkFeature(fp);
		create.setSourceAnchor(worldAnchor);
		check(classTool.canCreate(create), "World cannot create class");
		create.setSourceAnchor(baseAnchor);
		check(!classTool.canCreate(create) && !classTool.canStartConnection(create), "Class can contain class");
		check(new CreateActionLinkFeature(fp).canStartConnection(create), "Class cannot create action");
		check(!new CreateReflexLinkFeature(fp).canStartConnection(create), "Class can create reflex");
		check(!new CreateSubGridLinkFeature(fp).canStartConnection(create), "Class can create grid");
		check(!new CreateSubSpeciesLinkFeature(fp).canStartConnection(create), "Class can create species");
		create.setTargetAnchor(childAnchor);
		check(new CreateClassInheritingLinkFeature(fp).canCreate(create), "Class inheritance unavailable");
		check(!new CreateInheritingLinkFeature(fp).canCreate(create), "Species inheritance tool accepts class");
		create.setTargetAnchor(animalAnchor);
		check(!new CreateClassInheritingLinkFeature(fp).canCreate(create), "Class can parent species");
		create.setSourceAnchor(animalAnchor);
		create.setTargetAnchor(baseAnchor);
		check(!new CreateInheritingLinkFeature(fp).canCreate(create), "Species can parent class");
		final var add = new AddContext();
		add.setNewObject(base);
		add.setTargetContainer(diagram);
		check(new AddSpeciesFeature(fp).canAdd(add), "Class box cannot be drawn");
		checkServices(diagram, baseAnchor);
		System.out.println("PASS: " + checks + " class import, XMI, generation, detached preview, parent, edit/undo/redo and palette checks");
	}

	private static void checkServices(final Diagram diagram, final Anchor classAnchor) {
		final IFeatureProvider[] featureProvider = new IFeatureProvider[1];
		final var type = (org.eclipse.graphiti.dt.IDiagramTypeProvider) Proxy.newProxyInstance(
				org.eclipse.graphiti.dt.IDiagramTypeProvider.class.getClassLoader(),
				new Class<?>[] { org.eclipse.graphiti.dt.IDiagramTypeProvider.class }, (proxy, method, arguments) -> {
					return switch (method.getName()) {
						case "getFeatureProvider" -> featureProvider[0];
						case "getDiagram" -> diagram;
						default -> throw new UnsupportedOperationException(method.getName());
					};
				});
		final var provider = new gama.ui.diagram.editor.GamaFeatureProvider(type);
		featureProvider[0] = provider;
		final var mapType = gama.api.gaml.types.Types.MAP;
		final gama.ui.diagram.editor.MyGamaToolBehaviorProvider behavior;
		try {
			// The provider's empty frame map needs a type token without starting the GAMA platform.
			gama.api.gaml.types.Types.MAP = (gama.api.gaml.types.IContainerType) Proxy.newProxyInstance(
					gama.api.gaml.types.IContainerType.class.getClassLoader(),
					new Class<?>[] { gama.api.gaml.types.IContainerType.class }, (proxy, method, arguments) -> {
						if ("of".equals(method.getName())) return proxy;
						throw new UnsupportedOperationException(method.getName());
					});
			behavior = new gama.ui.diagram.editor.MyGamaToolBehaviorProvider(type);
		} finally {
			gama.api.gaml.types.Types.MAP = mapType;
		}
		final var palette = java.util.Arrays.asList(behavior.getPalette());
		final var classes = palette.stream().filter(c -> "Classes".equals(c.getLabel())).findFirst().orElseThrow();
		check(classes.getToolEntries().stream().map(org.eclipse.graphiti.palette.IToolEntry::getLabel).toList()
				.equals(List.of("has class", "is parent of")), "Classes palette does not contain exactly both class tools");
		check(palette.get(1) == classes, "Classes palette is not after Agents");
		check(palette.get(0).getToolEntries().stream().noneMatch(t -> "has class".equals(t.getLabel())),
				"Class creation also appears in Agents");
		final var doubleClick = (org.eclipse.graphiti.features.context.IDoubleClickContext) Proxy.newProxyInstance(
				org.eclipse.graphiti.features.context.IDoubleClickContext.class.getClassLoader(),
				new Class<?>[] { org.eclipse.graphiti.features.context.IDoubleClickContext.class },
				(proxy, method, arguments) -> {
					if ("getPictogramElements".equals(method.getName())) return new PictogramElement[] { classAnchor.getParent() };
					throw new UnsupportedOperationException(method.getName());
				});
		check(behavior.getDoubleClickFeature(doubleClick) instanceof gama.ui.diagram.features.edit.EditSpeciesFeature,
				"Class double-click is not wired to shared wizard feature");
		final var delete = new org.eclipse.graphiti.features.context.impl.DeleteContext(classAnchor.getParent());
		check(provider.getDeleteFeature(delete) instanceof gama.ui.diagram.features.others.CustomDeleteFeature
				&& provider.getDeleteFeature(delete).canDelete(delete), "Class deletion is unavailable");
	}
}
