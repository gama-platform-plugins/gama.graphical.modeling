package gama.ui.diagram.swt.editFrame;

import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EStructuralFeature;

import gama.ui.diagram.metamodel.EClassSpecies;
import gama.ui.diagram.metamodel.EGamaLink;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.GamaFactory;
import gama.ui.diagram.metamodel.GamaPackage;

/** Behavior kinds backed by the six behavior metamodel types. */
public enum DiagramBehavior {
	REFLEX("reflex", GamaPackage.Literals.EREFLEX, GamaPackage.Literals.EREFLEX_LINK),
	STATE("state", GamaPackage.Literals.ESTATE, GamaPackage.Literals.ESTATE_LINK),
	TASK("task", GamaPackage.Literals.ETASK, GamaPackage.Literals.ETASK_LINK),
	PLAN("plan", GamaPackage.Literals.EPLAN, GamaPackage.Literals.EPLAN_LINK),
	PERCEIVE("perceive", GamaPackage.Literals.EPERCEIVE, GamaPackage.Literals.EPERCEIVE_LINK),
	RULE("rule", GamaPackage.Literals.ERULE, GamaPackage.Literals.ERULE_LINK),
	INIT("init", GamaPackage.Literals.EREFLEX, GamaPackage.Literals.EREFLEX_LINK),
	ABORT("abort", GamaPackage.Literals.EREFLEX, GamaPackage.Literals.EREFLEX_LINK);

	private final String keyword;
	private final EClass nodeType;
	private final EClass linkType;

	DiagramBehavior(final String keyword, final EClass nodeType, final EClass linkType) {
		this.keyword = keyword;
		this.nodeType = nodeType;
		this.linkType = linkType;
	}

	public String keyword() { return keyword; }
	public boolean matches(final EGamaObject object) { return nodeType.isInstance(object); }
	public boolean lifecycle() { return nodeType == GamaPackage.Literals.EREFLEX; }
	private String linkName() { return lifecycle() ? "reflex" : keyword; }

	public static DiagramBehavior of(final EGamaObject object) {
		if (object instanceof gama.ui.diagram.metamodel.EReflex reflex) return valueOf(reflex.getKind().toUpperCase(java.util.Locale.ROOT));
		for (final var kind : values()) if (kind.nodeType.isInstance(object)) return kind;
		throw new IllegalArgumentException("Not an editable behavior: " + object.eClass().getName());
	}

	public EGamaObject create() {
		final var object = (EGamaObject) GamaFactory.eINSTANCE.create(nodeType);
		if (object instanceof gama.ui.diagram.metamodel.EReflex reflex) reflex.setKind(keyword);
		return object;
	}

	public String body(final EGamaObject object) {
		final var value = object.eGet(feature(nodeType, "gamlCode"));
		return value == null ? "" : (String) value;
	}

	public void body(final EGamaObject object, final String code) {
		object.eSet(feature(nodeType, "gamlCode"), code);
	}

	public ESpecies owner(final EGamaObject object) {
		final var owners = links(object).stream().map(link -> link.eGet(feature(linkType, "species"))).distinct().toList();
		if (owners.size() != 1 || !(owners.get(0) instanceof ESpecies owner)) {
			throw new IllegalArgumentException("The behavior must belong to exactly one species or experiment.");
		}
		return owner;
	}

	public List<EGamaLink> links(final org.eclipse.emf.ecore.EObject object) {
		final Object value = object.eGet(feature(object.eClass(), linkName() + "Links"));
		if (!(value instanceof List<?> links)) throw new IllegalStateException("Behavior links are not a list.");
		return links.stream().map(EGamaLink.class::cast).toList();
	}

	public EGamaLink attach(final ESpecies owner, final EGamaObject target) {
		if (owner instanceof EClassSpecies || owner.eResource() == null) {
			throw new IllegalArgumentException("This owner cannot contain a " + keyword + ".");
		}
		final var link = (EGamaLink) GamaFactory.eINSTANCE.create(linkType);
		link.setSource(owner);
		link.setTarget(target);
		link.eSet(feature(linkType, "species"), owner);
		link.eSet(feature(linkType, linkName()), target);
		if (owner.getModel() == null) {
			owner.eResource().getContents().addAll(List.of(target, link));
		} else {
			owner.getModel().getObjects().add(target);
			owner.getModel().getLinks().add(link);
		}
		addLink(owner, link);
		addLink(target, link);
		if (this == REFLEX && !owner.getReflexList().isEmpty()) owner.getReflexList().add(target.getName());
		return link;
	}

	@SuppressWarnings("unchecked")
	private void addLink(final org.eclipse.emf.ecore.EObject object, final EGamaLink link) {
		final var value = object.eGet(feature(object.eClass(), linkName() + "Links"));
		if (!(value instanceof org.eclipse.emf.common.util.EList<?> list)) {
			throw new IllegalStateException("Behavior links are not an EMF list.");
		}
		((org.eclipse.emf.common.util.EList<EGamaLink>) list).add(link);
	}

	private static EStructuralFeature feature(final EClass type, final String name) {
		final var feature = type.getEStructuralFeature(name);
		if (feature == null) throw new IllegalStateException("Missing " + type.getName() + "." + name);
		return feature;
	}
}
