package gama.ui.diagram.swt.editFrame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.Diagram;

import gama.api.gaml.types.Types;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EAction;
import gama.ui.diagram.metamodel.EActionLink;
import gama.ui.diagram.metamodel.EFacet;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EVariable;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.metamodel.GamaFactory;
import gaml.compiler.ui.wizards.action.ActionBodyContext;
import gaml.compiler.ui.wizards.action.ActionDraft;
import gaml.compiler.ui.wizards.action.ActionDraft.Argument;

/** Maps detached action drafts while retaining linked actions and unchanged argument objects. */
public final class ActionWizardAdapter {
	private ActionWizardAdapter() {}

	private static String text(final String value) {
		return value == null ? "" : value;
	}

	public static ActionDraft snapshot(final EAction action) {
		final String type = text(action.getReturnType());
		final boolean virtual = action.getFacets().stream()
				.anyMatch(f -> "virtual".equals(f.getName()) && "true".equals(text(f.getValue()).trim()));
		return new ActionDraft(text(action.getName()), type.isBlank() ? "action" : type, virtual,
				action.getVariables().stream().map(v -> new Argument(text(v.getName()), text(v.getType()), text(v.getInit()))).toList(),
				text(action.getGamlCode()));
	}

	public static ESpecies owner(final EAction action) {
		final var owners = action.getActionLinks().stream().map(link -> link.getSpecies()).distinct().toList();
		if (owners.size() != 1 || owners.get(0) == null) {
			throw new IllegalArgumentException("An action must belong to exactly one world, species or class.");
		}
		return owners.get(0);
	}

	public static void apply(final EAction action, final ActionDraft draft) {
		final String error = draftError(action, action.getActionLinks().isEmpty() ? null : owner(action), draft);
		if (error != null) throw new IllegalArgumentException(error);
		final boolean virtualChanged = snapshot(action).virtual() != draft.virtual();
		action.setName(draft.name());
		action.setReturnType("action".equals(draft.returnType()) ? "" : draft.returnType());
		action.setGamlCode(draft.body());
		EFacet virtual = action.getFacets().stream().filter(f -> "virtual".equals(f.getName())).findFirst().orElse(null);
		if (virtual == null && draft.virtual()) {
			virtual = GamaFactory.eINSTANCE.createEFacet();
			virtual.setName("virtual");
			action.getFacets().add(virtual);
		}
		if (virtual != null && virtualChanged) virtual.setValue(Boolean.toString(draft.virtual()));
		final Map<String, EVariable> previous = new LinkedHashMap<>();
		for (final EVariable argument : action.getVariables()) previous.put(argument.getName(), argument);
		final List<EVariable> arguments = new ArrayList<>();
		for (final Argument value : draft.arguments()) {
			EVariable argument = previous.get(value.name());
			if (argument == null) argument = GamaFactory.eINSTANCE.createEVariable();
			argument.setName(value.name());
			argument.setType(value.type());
			argument.setInit(value.defaultValue());
			argument.setOwner(action);
			arguments.add(argument);
		}
		action.getVariables().clear();
		action.getVariables().addAll(arguments);
	}

	private static String draftError(final EAction action, final ESpecies owner, final ActionDraft draft) {
		final String error = draft.validationError();
		if (error != null) return error;
		if (owner != null && owner.getActionLinks().stream().anyMatch(link -> link.getAction() != action
				&& link.getAction() != null && draft.name().equals(link.getAction().getName()))) {
			return "An action with this name already exists in this owner.";
		}
		return null;
	}

	public static List<String> typeNames(final Diagram diagram) {
		final var names = new TreeSet<String>();
		for (final String type : Types.getTypeNames()) names.add(type);
		final var contents = diagram.eResource().getAllContents();
		while (contents.hasNext()) {
			if (contents.next() instanceof ESpecies species && !(species instanceof EWorldAgent) && !(species instanceof EExperiment)
					&& species.getName() != null && !species.getName().isBlank()) names.add(species.getName());
		}
		names.removeAll(List.of("unknown", "void", "action"));
		final var result = new ArrayList<String>();
		result.add("action");
		result.addAll(names);
		return result;
	}

	public static URI previewURI(final Diagram diagram) {
		return diagram.eResource().getURI().trimFragment().trimFileExtension().appendFileExtension("action-wizard.gaml");
	}

	public static String validate(final IFeatureProvider fp, final Diagram diagram, final EAction action,
			final ESpecies creationOwner, final ActionDraft draft) {
		final ESpecies owner = creationOwner == null ? owner(action) : creationOwner;
		if (owner.eResource() != diagram.eResource()
				|| creationOwner == null && action.eResource() != diagram.eResource()) {
			return "The action or its owner is no longer part of this diagram.";
		}
		final String error = draftError(action, owner, draft);
		if (error != null) return error;
		final String source = candidateModel(fp, diagram, action, creationOwner, draft);
		if (source.isBlank()) return "The diagram needs a world box before an action can be validated.";
		return DiagramDraftValidation.validate(source, previewURI(diagram));
	}

	static String candidateModel(final IFeatureProvider fp, final Diagram diagram, final EAction action,
			final ESpecies creationOwner, final ActionDraft draft) {
		return candidateModel(fp, diagram, action, creationOwner, draft, null);
	}

	private static String candidateModel(final IFeatureProvider fp, final Diagram diagram, final EAction action,
			final ESpecies creationOwner, final ActionDraft draft, final String bodyMarker) {
		final var copier = new EcoreUtil.Copier(true, false);
		final var copies = copier.copyAll(diagram.eResource().getContents());
		copier.copyReferences();
		final var resource = new XMIResourceImpl(diagram.eResource().getURI());
		resource.getContents().addAll(copies);
		EAction target = (EAction) copier.get(action);
		if (creationOwner != null) {
			target = GamaFactory.eINSTANCE.createEAction();
			final var owner = (ESpecies) copier.get(creationOwner);
			attach(owner, target);
		}
		if (target == null) throw new IllegalArgumentException("The action is no longer part of this diagram.");
		apply(target, draft);
		if (bodyMarker != null) target.setGamlCode(bodyMarker);
		return ModelGenerator.generateModel(fp, (Diagram) copier.get(diagram), null);
	}

	/** Prefix/suffix are generated from a copy with a unique body marker, never split on source braces. */
	public static ActionBodyContext bodyContext(final IFeatureProvider fp, final Diagram diagram, final EAction action,
			final ESpecies creationOwner, final ActionDraft draft) {
		final String marker = "/*action-body-" + UUID.randomUUID() + "*/";
		final var concrete = new ActionDraft(draft.name(), draft.returnType(), false, draft.arguments(), draft.body());
		final String source = candidateModel(fp, diagram, action, creationOwner, concrete, marker);
		final int start = source.indexOf(marker);
		if (start < 0 || source.indexOf(marker, start + marker.length()) >= 0) {
			throw new IllegalStateException("Could not locate the action body in the generated model.");
		}
		return new ActionBodyContext(previewURI(diagram), source.substring(0, start), source.substring(start + marker.length()));
	}

	public static EActionLink attach(final ESpecies owner, final EAction action) {
		if (owner.eResource() == null) throw new IllegalArgumentException("The action owner is not in a diagram.");
		final var link = GamaFactory.eINSTANCE.createEActionLink();
		link.setSpecies(owner);
		link.setAction(action);
		link.setSource(owner);
		link.setTarget(action);
		if (owner.getModel() == null) {
			owner.eResource().getContents().add(action);
			owner.eResource().getContents().add(link);
		} else {
			owner.getModel().getObjects().add(action);
			owner.getModel().getLinks().add(link);
		}
		owner.getActionLinks().add(link);
		action.getActionLinks().add(link);
		return link;
	}
}
