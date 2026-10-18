package gama.ui.diagram.swt.editFrame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.Diagram;

import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EClassSpecies;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.GamaFactory;
import gaml.compiler.ui.wizards.action.ActionBodyContext;
import gaml.compiler.ui.wizards.behavior.BehaviorDraft;
import gaml.compiler.ui.wizards.behavior.BehaviorKind;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Facet;

public final class BehaviorWizardAdapter {
	private BehaviorWizardAdapter() {}

	public static BehaviorDraft snapshot(final EGamaObject object) {
		final var kind = DiagramBehavior.of(object);
		return new BehaviorDraft(BehaviorKind.of(kind.keyword()), object.getName() == null ? "" : object.getName(),
				object.getFacets().stream().map(f -> new Facet(f.getName(), f.getValue())).toList(), kind.body(object));
	}

	public static void apply(final EGamaObject object, final BehaviorDraft draft) {
		final var kind = DiagramBehavior.of(object);
		final var requested = DiagramBehavior.valueOf(draft.kind().name());
		if (kind != requested && !(kind.lifecycle() && requested.lifecycle())) {
			throw new IllegalArgumentException("An existing behavior cannot change its kind.");
		}
		final String error = draft.validationError();
		if (error != null) throw new IllegalArgumentException(error);
		final String oldName = object.getName();
		if (kind.lifecycle() && (!java.util.Objects.equals(oldName, draft.name()) || kind != requested)
				&& !kind.links(object).isEmpty()) {
			final var owner = kind.owner(object);
			final var ordered = ModelGenerator.orderedReflexes(owner);
			final var names = ordered.stream()
					.filter(link -> link.getReflex() == object ? requested == DiagramBehavior.REFLEX
							: "reflex".equals(link.getReflex().getKind()))
					.map(link -> link.getReflex() == object ? draft.name() : link.getReflex().getName())
					.map(name -> name == null ? "" : name).toList();
			owner.getReflexList().clear();
			if (names.stream().distinct().count() == names.size()) owner.getReflexList().addAll(names);
			else {
				// The legacy name list cannot represent multiple anonymous entries.
				owner.getReflexLinks().clear();
				owner.getReflexLinks().addAll(ordered);
			}
		}
		object.setName(draft.name());
		if (object instanceof gama.ui.diagram.metamodel.EReflex reflex) reflex.setKind(requested.keyword());
		kind.body(object, draft.body());
		final var oldFacets = new LinkedHashMap<String, gama.ui.diagram.metamodel.EFacet>();
		for (final var facet : object.getFacets()) oldFacets.put(facet.getName(), facet);
		final var facets = draft.facets().stream().map(value -> {
			var facet = oldFacets.get(value.name());
			if (facet == null) facet = GamaFactory.eINSTANCE.createEFacet();
			facet.setName(value.name());
			facet.setValue(value.value());
			return facet;
		}).toList();
		object.getFacets().clear();
		object.getFacets().addAll(facets);
	}

	public static String validate(final IFeatureProvider fp, final Diagram diagram, final EGamaObject object,
			final ESpecies creationOwner, final BehaviorDraft draft) {
		final var kind = DiagramBehavior.valueOf(draft.kind().name());
		final var owner = creationOwner == null ? kind.owner(object) : creationOwner;
		if (owner instanceof EClassSpecies || owner.eResource() != diagram.eResource()
				|| creationOwner == null && object.eResource() != diagram.eResource()) {
			return "The behavior or its owner is no longer available for editing.";
		}
		final String error = draft.validationError();
		if (error != null) return error;
		if (!draft.name().isBlank() && java.util.Arrays.stream(DiagramBehavior.values())
				.flatMap(siblingKind -> siblingKind.links(owner).stream())
				.anyMatch(link -> link.getTarget() != object && draft.name().equals(link.getTarget().getName()))) {
			return "A behavior with this name already exists in this owner.";
		}
		final String source = candidateModel(fp, diagram, object, creationOwner, draft);
		if (source.isBlank()) return "The diagram needs a world box before a behavior can be validated.";
		return DiagramDraftValidation.validate(source, previewURI(diagram));
	}

	public static java.util.Set<BehaviorKind> selectableKinds(final DiagramBehavior kind) {
		return BehaviorKind.of(kind.keyword()).convertibleKinds();
	}

	private static org.eclipse.emf.common.util.URI previewURI(final Diagram diagram) {
		return diagram.eResource().getURI().trimFragment().trimFileExtension().appendFileExtension("behavior-wizard.gaml");
	}

	static String candidateModel(final IFeatureProvider fp, final Diagram diagram, final EGamaObject object,
			final ESpecies creationOwner, final BehaviorDraft draft) {
		final var copier = new EcoreUtil.Copier(true, false);
		final var contents = copier.copyAll(diagram.eResource().getContents());
		copier.copyReferences();
		final var resource = new XMIResourceImpl(diagram.eResource().getURI());
		resource.getContents().addAll(contents);
		final var kind = DiagramBehavior.valueOf(draft.kind().name());
		final EGamaObject target;
		if (creationOwner == null) {
			target = (EGamaObject) copier.get(object);
			if (target == null) throw new IllegalArgumentException("The behavior is no longer in this diagram.");
			apply(target, draft);
		} else {
			target = kind.create();
			apply(target, draft);
			kind.attach((ESpecies) copier.get(creationOwner), target);
		}
		return ModelGenerator.generateModel(fp, (Diagram) copier.get(diagram), null);
	}

	public static ActionBodyContext bodyContext(final IFeatureProvider fp, final Diagram diagram,
			final EGamaObject object, final ESpecies creationOwner, final BehaviorDraft draft) {
		if (draft.kind() == BehaviorKind.RULE) {
			return new ActionBodyContext(previewURI(diagram),
					candidateModel(fp, diagram, object, creationOwner, draft), "");
		}
		final String marker = "/*behavior-body-" + UUID.randomUUID() + "*/";
		final var marked = new BehaviorDraft(draft.kind(), draft.name(), draft.facets(), marker);
		final String source = candidateModel(fp, diagram, object, creationOwner, marked);
		final int start = source.indexOf(marker);
		if (start < 0 || source.indexOf(marker, start + marker.length()) >= 0) {
			throw new IllegalStateException("Could not locate the behavior body in the generated model.");
		}
		return new ActionBodyContext(previewURI(diagram), source.substring(0, start), source.substring(start + marker.length()));
	}
}
