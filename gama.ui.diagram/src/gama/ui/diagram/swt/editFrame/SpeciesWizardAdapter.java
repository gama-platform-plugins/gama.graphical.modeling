package gama.ui.diagram.swt.editFrame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.features.context.impl.AddConnectionContext;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.xtext.resource.SynchronizedXtextResourceSet;

import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EFacet;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EGrid;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.metamodel.EInheritLink;
import gama.ui.diagram.metamodel.EVariable;
import gama.ui.diagram.metamodel.GamaFactory;
import gaml.compiler.resource.GamlResource;
import gaml.compiler.resource.GamlResourceServices;
import gaml.compiler.ui.wizards.species.SpeciesDraft;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Facet;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Variable;
import gaml.compiler.ui.wizards.species.SpeciesWizardContext;
import gaml.compiler.ui.wizards.species.SpeciesWizardContext.InheritedAttribute;

/** Converts detached wizard values without replacing species, links, or unchanged member objects. */
public final class SpeciesWizardAdapter {
	private SpeciesWizardAdapter() {}

	private static String text(final String value) {
		return value == null ? "" : value;
	}

	private static boolean editableFacet(final String name) {
		return !"name".equals(name) && !"skills".equals(name) && !"parent".equals(name);
	}

	public static SpeciesDraft snapshot(final ESpecies species) {
		final List<Facet> facets = new ArrayList<>(species.getFacets().stream()
				.filter(facet -> editableFacet(facet.getName()))
				.map(facet -> new Facet(text(facet.getName()), text(facet.getValue()))).toList());
		if (species.getInheritsFrom() != null) {
			facets.add(new Facet("parent", species.getInheritsFrom().getName()));
		} else {
			species.getFacets().stream().filter(facet -> "parent".equals(facet.getName()))
					.forEach(facet -> facets.add(new Facet("parent", text(facet.getValue()))));
		}
		final List<Variable> variables = species.getVariables().stream()
				.map(var -> new Variable(text(var.getName()), text(var.getType()), text(var.getInit()),
						text(var.getUpdate()), text(var.getFunction()), text(var.getMin()), text(var.getMax()))).toList();
		final List<String> reflexes = new ArrayList<>();
		for (final var link : species.getReflexLinks()) {
			if (link.getTarget() != null && link.getTarget().getName() != null) {
				reflexes.add(link.getTarget().getName());
			}
		}
		final List<String> order = new ArrayList<>();
		for (final String name : species.getReflexList()) {
			if (reflexes.contains(name) && !order.contains(name)) order.add(name);
		}
		for (final String name : reflexes) {
			if (!order.contains(name)) order.add(name);
		}
		return new SpeciesDraft(text(species.getName()), List.copyOf(species.getSkills()), facets,
				variables, text(species.getInit()), order);
	}

	public static SpeciesDraft modelSnapshot(final Diagram diagram, final EWorldAgent world) {
		final var draft = snapshot(world);
		return new SpeciesDraft(diagram.getName(), draft.skills(), draft.facets(), draft.variables(),
				draft.init(), draft.reflexOrder());
	}

	public static void applyModel(final Diagram diagram, final EWorldAgent world, final SpeciesDraft draft) {
		final var body = new SpeciesDraft(text(world.getName()), draft.skills(),
				draft.facets(), draft.variables(),
				draft.init(), draft.reflexOrder());
		apply(world, body, false);
		diagram.setName(draft.name());
	}

	public static void apply(final ESpecies species, final SpeciesDraft draft) {
		apply(species, draft, true);
	}

	private static void apply(final ESpecies species, final SpeciesDraft draft, final boolean editParent) {
		for (final Facet value : draft.facets()) {
			if (!editableFacet(value.name()) && !"parent".equals(value.name())) {
				throw new IllegalArgumentException("Name and skills have dedicated fields.");
			}
		}
		if (editParent) {
			final String parentName = parentName(draft);
			final ESpecies parent = resolveParent(species, parentName);
			applyParent(species, parent, parentName, draft.facets().stream().anyMatch(f -> "parent".equals(f.name())));
		}
		species.setName(draft.name());
		species.getSkills().clear();
		species.getSkills().addAll(draft.skills());
		species.setInit(draft.init());
		species.getReflexList().clear();
		species.getReflexList().addAll(draft.reflexOrder());
		final Map<String, EFacet> oldFacets = new LinkedHashMap<>();
		final List<EFacet> facets = new ArrayList<>();
		for (final EFacet facet : species.getFacets()) {
			if (editableFacet(facet.getName())) {
				oldFacets.put(facet.getName(), facet);
			} else {
				facets.add(facet);
			}
		}
		for (final Facet value : draft.facets()) {
			if ("parent".equals(value.name())) continue;
			EFacet facet = oldFacets.get(value.name());
			if (facet == null) facet = GamaFactory.eINSTANCE.createEFacet();
			facet.setName(value.name());
			facet.setValue(value.value());
			facets.add(facet);
		}
		species.getFacets().clear();
		species.getFacets().addAll(facets);
		final Map<String, EVariable> oldVariables = new LinkedHashMap<>();
		for (final EVariable variable : species.getVariables()) oldVariables.put(variable.getName(), variable);
		final List<EVariable> variables = new ArrayList<>();
		for (final Variable value : draft.variables()) {
			EVariable variable = oldVariables.get(value.name());
			if (variable == null) variable = GamaFactory.eINSTANCE.createEVariable();
			variable.setName(value.name());
			variable.setType(value.type());
			variable.setInit(value.init());
			variable.setUpdate(value.update());
			variable.setFunction(value.function());
			variable.setMin(value.min());
			variable.setMax(value.max());
			variables.add(variable);
		}
		species.getVariables().clear();
		species.getVariables().addAll(variables);
	}

	private static String parentName(final SpeciesDraft draft) {
		return draft.facets().stream().filter(f -> "parent".equals(f.name())).map(Facet::value)
				.findFirst().orElse("agent");
	}

	private static List<ESpecies> speciesInResource(final ESpecies species) {
		final List<ESpecies> result = new ArrayList<>();
		if (species.eResource() != null) {
			final var contents = species.eResource().getAllContents();
			while (contents.hasNext()) {
				if (contents.next() instanceof ESpecies candidate
						&& !(candidate instanceof EWorldAgent) && !(candidate instanceof EExperiment)) result.add(candidate);
			}
		}
		return result;
	}

	private static boolean cyclic(final ESpecies species, final ESpecies parent) {
		final Set<ESpecies> visited = new HashSet<>();
		for (ESpecies current = parent; current != null; current = current.getInheritsFrom()) {
			if (current == species || !visited.add(current)) return true;
		}
		return false;
	}

	public static SpeciesWizardContext wizardContext(final ESpecies context) {
		return wizardContext(context, context);
	}

	public static SpeciesWizardContext wizardContext(final ESpecies context, final ESpecies edited) {
		return SpeciesWizardContext.builtIns().and(new SpeciesWizardContext() {
			@Override
			public List<InheritedAttribute> attributes(final SpeciesDraft selection) {
				return localAttributes(context, selection);
			}

			@Override
			public List<String> mirrorNames(final List<String> fallback) {
				return SpeciesWizardAdapter.mirrorNames(context);
			}

			@Override
			public List<String> parentNames(final List<String> candidates) {
				final List<ESpecies> available = speciesInResource(context);
				return candidates.stream().filter(name -> {
					final List<ESpecies> matches = available.stream()
							.filter(species -> name.equals(species.getName())).toList();
					return matches.isEmpty() || matches.size() == 1
							&& matches.get(0) != edited && !cyclic(edited, matches.get(0));
				}).toList();
			}
		});
	}

	static List<String> mirrorNames(final ESpecies context) {
		final List<ESpecies> available = speciesInResource(context);
		return available.stream().map(ESpecies::getName).filter(name -> name != null && !name.isBlank())
				.filter(name -> available.stream().filter(species -> name.equals(species.getName())).count() == 1)
				.sorted().toList();
	}

	static List<InheritedAttribute> localAttributes(final ESpecies context, final SpeciesDraft selection) {
		final Map<String, InheritedAttribute> attributes = new LinkedHashMap<>();
		final List<ESpecies> available = speciesInResource(context);
		inheritAttributes(available, parentName(selection), "Parent", attributes);
		final String mirror = selection.facets().stream().filter(facet -> "mirrors".equals(facet.name()))
				.map(Facet::value).findFirst().orElse("");
		if (!mirror.isBlank() && available.stream().filter(species -> mirror.equals(species.getName())).count() == 1) {
			attributes.put("target", new InheritedAttribute(
					new Variable("target", mirror, "", "", "", "", ""), "Mirror: " + mirror, true));
		}
		return List.copyOf(attributes.values());
	}

	private static void inheritAttributes(final List<ESpecies> available, final String name, final String source,
			final Map<String, InheritedAttribute> attributes) {
		final List<ESpecies> matches = available.stream().filter(species -> name.equals(species.getName())).toList();
		if (matches.size() != 1) return;
		final List<ESpecies> lineage = new ArrayList<>();
		final Set<ESpecies> visited = new HashSet<>();
		for (ESpecies current = matches.get(0); current != null && visited.add(current);
				current = current.getInheritsFrom()) {
			lineage.add(0, current);
		}
		for (final ESpecies species : lineage) {
			for (final EVariable variable : species.getVariables()) {
				final Variable value = new Variable(text(variable.getName()), text(variable.getType()),
						text(variable.getInit()), text(variable.getUpdate()), text(variable.getFunction()),
						text(variable.getMin()), text(variable.getMax()));
				attributes.put(value.name(), new InheritedAttribute(value, source + ": " + species.getName(), true));
			}
		}
	}

	public static List<String> parentNames(final ESpecies context, final ESpecies edited) {
		final List<String> names = new ArrayList<>();
		names.add("agent");
		for (final ESpecies candidate : speciesInResource(context)) {
			if (candidate != edited && !cyclic(edited, candidate)
					&& candidate.getName() != null && !names.contains(candidate.getName())) names.add(candidate.getName());
		}
		return names;
	}

	private static ESpecies resolveParent(final ESpecies species, final String name) {
		if ("agent".equals(name)) return null;
		final List<ESpecies> matches = speciesInResource(species).stream()
				.filter(candidate -> name.equals(candidate.getName())).toList();
		if (matches.size() > 1) throw new IllegalArgumentException("Parent species name is ambiguous: " + name);
		if (matches.isEmpty()) {
			if (SpeciesWizardContext.builtInParent(name) != null) return null;
			final boolean existing = species.getFacets().stream()
					.anyMatch(f -> "parent".equals(f.getName()) && name.equals(f.getValue()));
			if (existing) return null;
			throw new IllegalArgumentException("Parent species is not available in this diagram: " + name);
		}
		if (cyclic(species, matches.get(0))) throw new IllegalArgumentException("A species cannot inherit from itself or its descendants.");
		return matches.get(0);
	}

	private static void applyParent(final ESpecies species, final ESpecies parent, final String name,
			final boolean explicit) {
		if (species.getInheritsFrom() != parent) species.setInheritsFrom(parent);
		final List<EInheritLink> links = new ArrayList<>();
		if (species.eResource() != null) {
			final var contents = species.eResource().getAllContents();
			while (contents.hasNext()) {
				if (contents.next() instanceof EInheritLink link && link.getChild() == species) links.add(link);
			}
		}
		EInheritLink retained = null;
		for (final EInheritLink link : links) {
			if (parent != null && retained == null) {
				retained = link;
				if (link.getParent() != null) link.getParent().getInheritingLinks().remove(link);
				link.setParent(parent);
				link.setSource(parent);
				link.setTarget(species);
				parent.getInheritingLinks().add(link);
			} else {
				EcoreUtil.delete(link, true);
			}
		}
		if (parent != null && retained == null) {
			final var link = GamaFactory.eINSTANCE.createEInheritLink();
			link.setParent(parent);
			link.setChild(species);
			link.setSource(parent);
			link.setTarget(species);
			parent.getInheritingLinks().add(link);
			species.eResource().getContents().add(link);
		}
		species.getFacets().removeIf(f -> "parent".equals(f.getName()));
		if (parent == null && explicit) {
			final var facet = GamaFactory.eINSTANCE.createEFacet();
			facet.setName("parent");
			facet.setValue(name);
			species.getFacets().add(facet);
		}
	}

	public static List<Connection> parentConnections(final Diagram diagram, final ESpecies species) {
		return diagram.getConnections().stream().filter(connection -> connection.getLink() != null
				&& connection.getLink().getBusinessObjects().stream()
						.anyMatch(bo -> bo instanceof EInheritLink link && link.getChild() == species)).toList();
	}

	public static void syncParentConnection(final IFeatureProvider fp, final Diagram diagram, final ESpecies species,
			final List<Connection> previous) {
		final var pe = Graphiti.getPeService();
		if (species.getInheritsFrom() == null) {
			for (final var connection : previous) pe.deletePictogramElement(connection);
			return;
		}
		final Anchor source = anchor(diagram, species.getInheritsFrom());
		final Anchor target = anchor(diagram, species);
		if (source == null || target == null) throw new IllegalStateException("Parent and species need diagram boxes.");
		if (previous.size() == 1 && !previous.get(0).getLink().getBusinessObjects().isEmpty()) {
			previous.get(0).setStart(source);
			previous.get(0).setEnd(target);
			return;
		}
		for (final var connection : previous) pe.deletePictogramElement(connection);
		for (final EInheritLink link : species.getInheritsFrom().getInheritingLinks()) {
			if (link.getChild() != species) continue;
			final var context = new AddConnectionContext(source, target);
			context.setNewObject(link);
			if (fp.addIfPossible(context) == null) throw new IllegalStateException("Could not draw the parent relation.");
		}
	}

	private static Anchor anchor(final Diagram diagram, final ESpecies species) {
		for (final var shape : diagram.getChildren()) {
			if (shape.getLink() != null
					&& shape.getLink().getBusinessObjects().contains(species)) {
				return Graphiti.getPeService().getChopboxAnchor(shape);
			}
		}
		return null;
	}

	/** Validate a complete candidate on copies; neither the live diagram nor its validation status is changed. */
	public static String validate(final IFeatureProvider fp, final Diagram diagram, final ESpecies species,
			final ESpecies owner, final SpeciesDraft draft) {
		if (species instanceof EGrid && draft.facets().stream()
				.anyMatch(facet -> "mirrors".equals(facet.name()) && !facet.value().isBlank())) {
			return "A grid cannot mirror another species.";
		}
		for (final Facet facet : draft.facets()) {
			if (!editableFacet(facet.name()) && !"parent".equals(facet.name())) return "Use the name/skills fields, not a duplicate facet.";
		}
		try {
			if (!(species instanceof EWorldAgent)) {
			if (owner == null) resolveParent(species, parentName(draft));
			else if (!"agent".equals(parentName(draft))) {
				final long matches = speciesInResource(owner).stream()
						.filter(candidate -> parentName(draft).equals(candidate.getName())).count();
				if (matches > 1 || matches == 0 && SpeciesWizardContext.builtInParent(parentName(draft)) == null) {
					return "Choose an unambiguous parent from the available diagram or built-in species.";
				}
			}
			if (owner == null && parentName(draft).equals(draft.name()) && !"agent".equals(parentName(draft))) {
				return "A species cannot inherit from itself.";
			}
			}
		} catch (final IllegalArgumentException error) {
			return error.getMessage();
		}
		if (owner == null && !snapshot(species).reflexOrder().stream().sorted().toList()
				.equals(draft.reflexOrder().stream().sorted().toList())) {
			return "Reflex order must contain each linked reflex exactly once; edit reflex boxes to add or rename them.";
		}
		if (owner != null && !draft.reflexOrder().isEmpty()) {
			return "Create reflex boxes after creating the species; this list only orders existing reflexes.";
		}
		if (owner == null && species.eResource() != diagram.eResource()) {
			return "The species is no longer part of this diagram.";
		}
		if (owner != null && owner.eResource() != diagram.eResource()) {
			return "The containing species is no longer part of this diagram.";
		}
		final String gaml = candidateModel(fp, diagram, species, owner, draft);
		if (gaml.isBlank()) return "The diagram needs a world box before a species can be validated.";
		final URI base = diagram.eResource().getURI().trimFragment().trimFileExtension();
		final URI uri = base.appendFileExtension("species-wizard.gaml");
		final var resources = new SynchronizedXtextResourceSet();
		resources.setClasspathURIContext(ModelGenerator.class);
		final var resource = (GamlResource) resources.createResource(uri);
		try (final var stream = new ByteArrayInputStream(gaml.getBytes(StandardCharsets.UTF_8))) {
			resource.load(stream, null);
			if (!resource.getErrors().isEmpty()) return resource.getErrors().get(0).getMessage();
			resource.getValidationContext().clear();
			final var description = resource.buildCompleteDescription();
			try {
				if (description == null) return "The candidate model could not be described.";
				final var validated = description.validate();
				for (final var error : resource.getValidationContext().getInternalErrors()) {
					if (error.isError()) return error.toString();
				}
				return validated == null ? "The candidate model could not be validated." : null;
			} finally {
				if (description != null) description.dispose();
			}
		} catch (final IOException error) {
			throw new IllegalStateException("Could not load the candidate species model", error);
		} finally {
			GamlResourceServices.discardValidationContext(resource);
			resource.unload();
			resources.getResources().clear();
		}
	}

	static String candidateModel(final IFeatureProvider fp, final Diagram diagram, final ESpecies species,
			final ESpecies owner, final SpeciesDraft draft) {
		final var copier = new EcoreUtil.Copier(true, false);
		final var copies = copier.copyAll(diagram.eResource().getContents());
		copier.copyReferences();
		final var previewResource = new XMIResourceImpl(diagram.eResource().getURI());
		previewResource.getContents().addAll(copies);
		final Diagram preview = (Diagram) copier.get(diagram);
		ESpecies target = (ESpecies) copier.get(species);
		if (owner != null) {
			target = GamaFactory.eINSTANCE.createESpecies();
			previewResource.getContents().add(target);
			final ESpecies copiedOwner = (ESpecies) copier.get(owner);
			final var link = GamaFactory.eINSTANCE.createESubSpeciesLink();
			link.setMacro(copiedOwner);
			link.setMicro(target);
			copiedOwner.getMicroSpeciesLinks().add(link);
			target.getMacroSpeciesLinks().add(link);
		}
		if (target instanceof EWorldAgent world) applyModel(preview, world, draft);
		else apply(target, draft);
		return ModelGenerator.generateModel(fp, preview, null);
	}
}
