package gama.ui.diagram.swt.editFrame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.Diagram;

import gama.ui.diagram.features.modelgeneration.ExperimentKind;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.EMonitor;
import gama.ui.diagram.metamodel.EParameter;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.metamodel.GamaFactory;
import gaml.compiler.ui.wizards.action.ActionBodyContext;
import gaml.compiler.ui.wizards.experiment.ExperimentDraft;
import gaml.compiler.ui.wizards.experiment.ExperimentDraft.Monitor;
import gaml.compiler.ui.wizards.experiment.ExperimentDraft.Parameter;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Facet;

public final class ExperimentWizardAdapter {
	private ExperimentWizardAdapter() {}

	private static String text(final String value) { return value == null ? "" : value; }

	private static List<Facet> facets(final EGamaObject object, final Set<String> excluded) {
		return object.getFacets().stream().filter(f -> !excluded.contains(f.getName()))
				.map(f -> new Facet(f.getName(), text(f.getValue()))).toList();
	}

	public static ExperimentDraft snapshot(final EExperiment experiment) {
		final var species = SpeciesWizardAdapter.snapshot(experiment);
		final var facets = new ArrayList<>(facets(experiment, Set.of("type", "name")));
		if (!experiment.getSkills().isEmpty() && facets.stream().noneMatch(f -> "skills".equals(f.name()))) {
			facets.add(new Facet("skills", experiment.getSkills().toString()));
		}
		if (experiment.getInheritsFrom() != null && facets.stream().noneMatch(f -> "parent".equals(f.name()))) {
			facets.add(new Facet("parent", experiment.getInheritsFrom().getName()));
		}
		return new ExperimentDraft(text(experiment.getName()), ExperimentKind.of(experiment), facets, species.variables(),
				text(experiment.getInit()), experiment.getParameters().stream().map(ExperimentWizardAdapter::parameter).toList(),
				experiment.getMonitors().stream().map(m -> new Monitor(text(m.getName()), text(m.getValue()),
						facets(m, Set.of("name", "value")))).toList());
	}

	private static Parameter parameter(final EParameter parameter) {
		return new Parameter(text(parameter.getName()), text(parameter.getVariable()),
				ModelGenerator.parameterFacets(parameter).entrySet().stream()
						.map(e -> new Facet(e.getKey(), e.getValue())).toList());
	}

	private static void applyFacets(final EGamaObject object, final List<Facet> values) {
		final var previous = new LinkedHashMap<String, gama.ui.diagram.metamodel.EFacet>();
		for (final var facet : object.getFacets()) previous.put(facet.getName(), facet);
		final var updated = values.stream().map(value -> {
			var facet = previous.get(value.name());
			if (facet == null) facet = GamaFactory.eINSTANCE.createEFacet();
			facet.setName(value.name());
			facet.setValue(value.value());
			return facet;
		}).toList();
		object.getFacets().clear();
		object.getFacets().addAll(updated);
	}

	public static String draftError(final EExperiment experiment, final ExperimentDraft draft) {
		final String error = draft.validationError();
		if (error != null) return error;
		final String parentName = facet(draft.facets(), "parent");
		if (parentName.equals(draft.name())) return "An experiment cannot inherit from itself.";
		if (experiment != null) {
			final var parents = parents(experiment, parentName);
			if (parents.size() > 1) return "The parent experiment name is ambiguous.";
			if (!parents.isEmpty()) {
				final var visited = new java.util.HashSet<gama.ui.diagram.metamodel.ESpecies>();
				for (var parent = (gama.ui.diagram.metamodel.ESpecies) parents.get(0);
						parent != null; parent = parent.getInheritsFrom()) {
					if (parent == experiment || !visited.add(parent)) return "Experiment inheritance cannot be cyclic.";
				}
			}
		}
		return null;
	}

	private static List<EExperiment> parents(final EExperiment experiment, final String name) {
		final List<EExperiment> result = new ArrayList<>();
		if (experiment.eResource() != null && !name.isBlank()) {
			final var contents = experiment.eResource().getAllContents();
			while (contents.hasNext()) {
				if (contents.next() instanceof EExperiment candidate && name.equals(candidate.getName())) result.add(candidate);
			}
		}
		return result;
	}

	public static void apply(final EExperiment experiment, final ExperimentDraft draft) {
		final String error = draftError(experiment, draft);
		if (error != null) throw new IllegalArgumentException(error);
		experiment.setName(draft.name());
		experiment.setInit(draft.init());
		// Skills expressions remain raw facets; changing them must not duplicate legacy skills.
		experiment.getSkills().clear();
		final String parentName = facet(draft.facets(), "parent");
		final var parents = parents(experiment, parentName);
		final var previousParentFacet = experiment.getFacets().stream()
				.filter(f -> "parent".equals(f.getName())).findFirst().orElse(null);
		final var previousTypeFacet = experiment.getFacets().stream()
				.filter(f -> "type".equals(f.getName())).findFirst().orElse(null);
		SpeciesWizardAdapter.applyParent(experiment, parents.isEmpty() ? null : parents.get(0), parentName, !parentName.isBlank());
		if (previousParentFacet != null) {
			experiment.getFacets().removeIf(f -> "parent".equals(f.getName()));
			experiment.getFacets().add(previousParentFacet);
		}
		applyFacets(experiment, draft.facets());
		if (previousTypeFacet != null) experiment.getFacets().add(previousTypeFacet);
		ExperimentKind.set(experiment, draft.type());
		SpeciesWizardAdapter.applyAttributes(experiment, draft.variables());
		final var oldParameters = new ArrayList<>(experiment.getParameters());
		final List<EParameter> parameters = new ArrayList<>();
		for (final var value : draft.parameters()) {
			var parameter = oldParameters.stream().filter(p -> text(p.getVariable()).equals(value.variable())
					&& text(p.getName()).equals(value.title())).findFirst().orElse(null);
			if (parameter == null) parameter = oldParameters.stream()
					.filter(p -> text(p.getVariable()).equals(value.variable()))
					.filter(p -> draft.parameters().stream().noneMatch(other -> text(p.getVariable()).equals(other.variable())
							&& text(p.getName()).equals(other.title()))).findFirst().orElse(null);
			if (parameter == null) parameter = GamaFactory.eINSTANCE.createEParameter();
			oldParameters.remove(parameter);
			parameter.setName(value.title());
			parameter.setVariable(value.variable());
			parameter.setInit(facet(value.facets(), "init"));
			parameter.setMin(facet(value.facets(), "min"));
			parameter.setMax(facet(value.facets(), "max"));
			parameter.setStep(facet(value.facets(), "step"));
			parameter.setAmong(facet(value.facets(), "among"));
			final String category = facet(value.facets(), "category");
			final boolean literal = category.length() >= 2 && (category.charAt(0) == '"' || category.charAt(0) == '\'')
					&& category.charAt(category.length() - 1) == category.charAt(0);
			parameter.setCategory(literal ? new org.eclipse.xtext.conversion.impl.STRINGValueConverter().toValue(category, null) : "");
			applyFacets(parameter, value.facets());
			attachMember(experiment, parameter);
			parameters.add(parameter);
		}
		experiment.getParameters().clear();
		experiment.getParameters().addAll(parameters);
		for (final var removed : oldParameters) EcoreUtil.delete(removed, true);
		final var oldMonitors = new ArrayList<>(experiment.getMonitors());
		final List<EMonitor> monitors = new ArrayList<>();
		for (final var value : draft.monitors()) {
			var monitor = oldMonitors.stream().filter(m -> text(m.getName()).equals(value.title())).findFirst().orElse(null);
			if (monitor == null) monitor = GamaFactory.eINSTANCE.createEMonitor();
			oldMonitors.remove(monitor);
			monitor.setName(value.title());
			monitor.setValue(value.value());
			applyFacets(monitor, value.facets());
			attachMember(experiment, monitor);
			monitors.add(monitor);
		}
		experiment.getMonitors().clear();
		experiment.getMonitors().addAll(monitors);
		for (final var removed : oldMonitors) EcoreUtil.delete(removed, true);
	}

	private static String facet(final List<Facet> facets, final String name) {
		return facets.stream().filter(f -> name.equals(f.name())).map(Facet::value).findFirst().orElse("");
	}

	private static void attachMember(final EExperiment experiment, final EGamaObject member) {
		if (member.eResource() != null) return;
		if (experiment.getModel() != null) experiment.getModel().getObjects().add(member);
		else if (experiment.eResource() != null) experiment.eResource().getContents().add(member);
		else throw new IllegalArgumentException("The experiment must be attached before adding members.");
	}

	public static List<String> variableNames(final EExperiment experiment, final EWorldAgent world) {
		final var names = new java.util.TreeSet<String>();
		if (world != null) for (final var variable : world.getVariables()) names.add(variable.getName());
		if (experiment != null) for (final var variable : experiment.getVariables()) names.add(variable.getName());
		return List.copyOf(names);
	}

	public static String validate(final IFeatureProvider fp, final Diagram diagram, final EExperiment experiment,
			final EWorldAgent creationOwner, final ExperimentDraft draft) {
		final String error = draftError(experiment, draft);
		if (error != null) return error;
		final var owner = creationOwner == null ? experiment.getExperimentLink().getSpecies() : creationOwner;
		if (owner.eResource() != diagram.eResource()
				|| creationOwner == null && experiment.eResource() != diagram.eResource()) {
			return "The experiment or its world is no longer part of this diagram.";
		}
		if (owner.getExperimentLinks().stream().anyMatch(link -> link.getExperiment() != experiment
				&& draft.name().equals(link.getExperiment().getName()))) return "An experiment with this name already exists.";
		final String source = candidateModel(fp, diagram, experiment, creationOwner, draft);
		if (source.isBlank()) return "The diagram needs a world box before an experiment can be validated.";
		final URI uri = diagram.eResource().getURI().trimFragment().trimFileExtension().appendFileExtension("experiment-wizard.gaml");
		return DiagramDraftValidation.validate(source, uri);
	}

	static String candidateModel(final IFeatureProvider fp, final Diagram diagram, final EExperiment experiment,
			final EWorldAgent creationOwner, final ExperimentDraft draft) {
		final var copier = new EcoreUtil.Copier(true, false);
		final var roots = copier.copyAll(diagram.eResource().getContents());
		copier.copyReferences();
		final var resource = new XMIResourceImpl(diagram.eResource().getURI());
		resource.getContents().addAll(roots);
		final EExperiment target;
		if (creationOwner != null) {
			target = GamaFactory.eINSTANCE.createEGUIExperiment();
			attach((EWorldAgent) copier.get(creationOwner), target);
		} else {
			target = (EExperiment) copier.get(experiment);
			if (target == null) throw new IllegalArgumentException("The experiment is no longer part of this diagram.");
		}
		apply(target, draft);
		return ModelGenerator.generateModel(fp, (Diagram) copier.get(diagram), null);
	}

	public static ActionBodyContext bodyContext(final IFeatureProvider fp, final Diagram diagram,
			final EExperiment experiment, final EWorldAgent creationOwner, final ExperimentDraft draft) {
		final String marker = "/*experiment-init-" + java.util.UUID.randomUUID() + "*/";
		final var marked = new ExperimentDraft(draft.name(), draft.type(), draft.facets(), draft.variables(),
				marker, draft.parameters(), draft.monitors());
		final String source = candidateModel(fp, diagram, experiment, creationOwner, marked);
		final int start = source.indexOf(marker);
		if (start < 0 || source.indexOf(marker, start + marker.length()) >= 0) {
			throw new IllegalStateException("Could not locate the experiment init in the generated model.");
		}
		final URI uri = diagram.eResource().getURI().trimFragment().trimFileExtension().appendFileExtension("experiment-wizard.gaml");
		return new ActionBodyContext(uri, source.substring(0, start), source.substring(start + marker.length()));
	}

	public static gama.ui.diagram.metamodel.EExperimentLink attach(final EWorldAgent owner, final EExperiment target) {
		final var link = GamaFactory.eINSTANCE.createEExperimentLink();
		link.setSpecies(owner);
		link.setExperiment(target);
		link.setSource(owner);
		link.setTarget(target);
		owner.getExperimentLinks().add(link);
		target.setExperimentLink(link);
		if (owner.getModel() == null) owner.eResource().getContents().addAll(List.of(target, link));
		else {
			owner.getModel().getObjects().add(target);
			owner.getModel().getLinks().add(link);
		}
		return link;
	}
}
