package gama.ui.diagram.features.modelgeneration;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import gama.api.compilation.descriptions.IDescription;
import gama.api.compilation.descriptions.IExperimentDescription;
import gama.api.compilation.descriptions.IModelDescription;
import gama.api.compilation.descriptions.ISpeciesDescription;
import gama.api.compilation.descriptions.IStatementDescription;
import gama.api.compilation.descriptions.ITypeDescription;
import gama.api.compilation.descriptions.IVariableDescription;
import gama.ui.diagram.metamodel.EAction;
import gama.ui.diagram.metamodel.EActionLink;
import gama.ui.diagram.metamodel.EAspect;
import gama.ui.diagram.metamodel.EAspectLink;
import gama.ui.diagram.metamodel.EDisplay;
import gama.ui.diagram.metamodel.EDisplayLink;
import gama.ui.diagram.metamodel.EEquation;
import gama.ui.diagram.metamodel.EEquationLink;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EExperimentLink;
import gama.ui.diagram.metamodel.EFacet;
import gama.ui.diagram.metamodel.EGUIExperiment;
import gama.ui.diagram.metamodel.EGamaLink;
import gama.ui.diagram.metamodel.EGamaModel;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.EInheritLink;
import gama.ui.diagram.metamodel.EMonitor;
import gama.ui.diagram.metamodel.EParameter;
import gama.ui.diagram.metamodel.EPerceive;
import gama.ui.diagram.metamodel.EPerceiveLink;
import gama.ui.diagram.metamodel.EPlan;
import gama.ui.diagram.metamodel.EPlanLink;
import gama.ui.diagram.metamodel.EReflex;
import gama.ui.diagram.metamodel.EReflexLink;
import gama.ui.diagram.metamodel.ERule;
import gama.ui.diagram.metamodel.ERuleLink;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EState;
import gama.ui.diagram.metamodel.EStateLink;
import gama.ui.diagram.metamodel.ESubSpeciesLink;
import gama.ui.diagram.metamodel.ETask;
import gama.ui.diagram.metamodel.ETaskLink;
import gama.ui.diagram.metamodel.EVariable;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.metamodel.GamaFactory;
import gaml.compiler.gaml.Block;
import gaml.compiler.gaml.FacetsAndBlock;
import gaml.compiler.gaml.S_Equations;

/**
 * Copies declarations into the editable diagram model without compiling executable GAMA symbols.
 */
public final class DescriptionDiagramImporter {

	private final GamaFactory factory = GamaFactory.eINSTANCE;
	private final EGamaModel model = factory.createEGamaModel();
	private final Map<ISpeciesDescription, ESpecies> species = new IdentityHashMap<>();

	private DescriptionDiagramImporter() {}

	public static EGamaModel importModel(final IModelDescription description) {
		final DescriptionDiagramImporter importer = new DescriptionDiagramImporter();
		importer.model.setName(description.getName());
		final EWorldAgent world = importer.factory.createEWorldAgent();
		world.setName("world");
		importer.model.getObjects().add(world);
		importer.species.put(description, world);
		importer.populateSpecies(description, world);
		for (final IExperimentDescription experiment : description.getExperiments()) {
			if (isDeclaredHere(experiment, description) && !importer.species.containsKey(experiment)) {
				importer.addSpecies(experiment, world);
			}
		}
		importer.connectInheritance();
		return importer.model;
	}

	private void addSpecies(final ISpeciesDescription description, final ESpecies owner) {
		if (species.containsKey(description)) return;
		final ESpecies target;
		if (description instanceof IExperimentDescription) {
			target = ((IExperimentDescription) description).isBatch() ? factory.createEBatchExperiment()
					: factory.createEGUIExperiment();
		} else {
			target = description.isGrid() ? factory.createEGrid() : factory.createESpecies();
		}
		target.setName(description.getName());
		model.getObjects().add(target);
		species.put(description, target);
		if (target instanceof EExperiment) {
			final EExperimentLink link = factory.createEExperimentLink();
			link.setSpecies(owner);
			link.setExperiment((EExperiment) target);
			owner.getExperimentLinks().add(link);
			((EExperiment) target).setExperimentLink(link);
			connect(link, owner, target);
		} else {
			final ESubSpeciesLink link = factory.createESubSpeciesLink();
			link.setMacro(owner);
			link.setMicro(target);
			owner.getMicroSpeciesLinks().add(link);
			target.getMacroSpeciesLinks().add(link);
			connect(link, owner, target);
		}
		populateSpecies(description, target);
	}

	private void populateSpecies(final ISpeciesDescription description, final ESpecies target) {
		copyFacets(description, target, Set.of("name", "parent", "skills", "type"));
		// The resolved skills also include inherited and implicit skills; only copy an explicit skills facet.
		if (description.hasFacet("skills")) {
			target.getSkills().addAll(description.getFacet("skills").getStrings(description, false));
		}
		for (final IDescription child : description.getOwnChildren()) {
			if (!isDeclaredHere(child, description)) continue;
			if (child instanceof IVariableDescription && !"parameter".equals(child.getKeyword())) {
				final EVariable variable = variable(child);
				variable.setOwner(target);
				target.getVariables().add(variable);
			} else if (child instanceof ISpeciesDescription) {
				addSpecies((ISpeciesDescription) child, target);
			} else if ("init".equals(child.getKeyword())) {
				target.setInit(body(child));
			} else if ("output".equals(child.getKeyword()) && target instanceof EExperiment) {
				importOutputs(child, (EExperiment) target);
			} else if ("parameter".equals(child.getKeyword()) && target instanceof EExperiment) {
				addParameter(child, (EExperiment) target);
			} else {
				addComponent(child, target);
			}
		}
	}

	static boolean isDeclaredHere(final IDescription description, final IDescription owner) {
		if (description.isBuiltIn() || description.getUnderlyingElement() == null
				|| description.getEnclosingDescription() != owner
				|| description instanceof IVariableDescription && ((IVariableDescription) description).isSynthetic()) {
			return false;
		}
		// Some compiler descriptions copy inherited members into their "own" collections.
		// A copy retains its source EObject; an explicit override has a different declaration.
		if (owner instanceof ITypeDescription) {
			final ITypeDescription parent = ((ITypeDescription) owner).getParent();
			if (parent != null && parent != owner) {
				final IDescription inherited;
				if (description instanceof IVariableDescription) {
					inherited = parent.getAttribute(description.getName());
				} else if ("action".equals(description.getKeyword())) {
					inherited = parent.getAction(description.getName());
				} else if (description instanceof ISpeciesDescription && parent instanceof ISpeciesDescription) {
					inherited = ((ISpeciesDescription) parent).getMicroSpecies(description.getName());
				} else {
					inherited = null;
				}
				if (inherited != null && inherited.getUnderlyingElement() == description.getUnderlyingElement()) {
					return false;
				}
			}
		}
		return true;
	}

	private EVariable variable(final IDescription description) {
		final EVariable variable = factory.createEVariable();
		variable.setName(description.getName());
		variable.setType(description.getGamlType().serializeToGaml(false));
		variable.setInit(facet(description, "init"));
		variable.setUpdate(facet(description, "update"));
		variable.setMin(facet(description, "min"));
		variable.setMax(facet(description, "max"));
		variable.setFunction(description.hasFacet("function") ? facet(description, "function")
				: facet(description, "value"));
		return variable;
	}

	private void addComponent(final IDescription description, final ESpecies owner) {
		if (!Set.of("action", "aspect", "reflex", "state", "task", "plan", "perceive", "rule", "equation")
				.contains(description.getKeyword())) return;
		final EGamaObject target;
		final EGamaLink link;
		final String code = body(description);
		switch (description.getKeyword()) {
			case "action" -> {
				final EAction action = factory.createEAction();
				action.setGamlCode(code);
				if (description.hasFacet("type")) action.setReturnType(facet(description, "type"));
				if (description instanceof IStatementDescription) {
					for (final IDescription argument : ((IStatementDescription) description).getFormalArgs()) {
						final EVariable variable = variable(argument);
						variable.setOwner(action);
						action.getVariables().add(variable);
					}
				}
				final EActionLink relation = factory.createEActionLink();
				relation.setSpecies(owner);
				relation.setAction(action);
				owner.getActionLinks().add(relation);
				action.getActionLinks().add(relation);
				target = action;
				link = relation;
			}
			case "aspect" -> {
				final EAspect aspect = factory.createEAspect();
				aspect.setDefineGamlCode(true);
				aspect.setGamlCode(code);
				final EAspectLink relation = factory.createEAspectLink();
				relation.setSpecies(owner);
				relation.setAspect(aspect);
				owner.getAspectLinks().add(relation);
				aspect.getAspectLinks().add(relation);
				target = aspect;
				link = relation;
			}
			case "reflex" -> {
				final EReflex reflex = factory.createEReflex();
				reflex.setGamlCode(code);
				final EReflexLink relation = factory.createEReflexLink();
				relation.setSpecies(owner);
				relation.setReflex(reflex);
				owner.getReflexLinks().add(relation);
				owner.getReflexList().add(description.getName());
				reflex.getReflexLinks().add(relation);
				target = reflex;
				link = relation;
			}
			case "state" -> {
				final EState state = factory.createEState();
				state.setGamlCode(code);
				final EStateLink relation = factory.createEStateLink();
				relation.setSpecies(owner);
				relation.setState(state);
				owner.getStateLinks().add(relation);
				state.getStateLinks().add(relation);
				target = state;
				link = relation;
			}
			case "task" -> {
				final ETask task = factory.createETask();
				task.setGamlCode(code);
				final ETaskLink relation = factory.createETaskLink();
				relation.setSpecies(owner);
				relation.setTask(task);
				owner.getTaskLinks().add(relation);
				task.getTaskLinks().add(relation);
				target = task;
				link = relation;
			}
			case "plan" -> {
				final EPlan plan = factory.createEPlan();
				plan.setGamlCode(code);
				final EPlanLink relation = factory.createEPlanLink();
				relation.setSpecies(owner);
				relation.setPlan(plan);
				owner.getPlanLinks().add(relation);
				plan.getPlanLinks().add(relation);
				target = plan;
				link = relation;
			}
			case "perceive" -> {
				final EPerceive perceive = factory.createEPerceive();
				perceive.setGamlCode(code);
				final EPerceiveLink relation = factory.createEPerceiveLink();
				relation.setSpecies(owner);
				relation.setPerceive(perceive);
				owner.getPerceiveLinks().add(relation);
				perceive.getPerceiveLinks().add(relation);
				target = perceive;
				link = relation;
			}
			case "rule" -> {
				final ERule rule = factory.createERule();
				rule.setGamlCode(code);
				final ERuleLink relation = factory.createERuleLink();
				relation.setSpecies(owner);
				relation.setRule(rule);
				owner.getRuleLinks().add(relation);
				rule.getRuleLinks().add(relation);
				target = rule;
				link = relation;
			}
			case "equation" -> {
				final EEquation equation = factory.createEEquation();
				equation.setGamlCode(code);
				final EEquationLink relation = factory.createEEquationLink();
				relation.setSpecies(owner);
				relation.setEquation(equation);
				owner.getEquationLinks().add(relation);
				equation.getEquationLinks().add(relation);
				target = equation;
				link = relation;
			}
			default -> { return; }
		}
		target.setName(description.getName());
		copyFacets(description, target, Set.of("name", "type"));
		model.getObjects().add(target);
		connect(link, owner, target);
	}

	private void importOutputs(final IDescription output, final EExperiment owner) {
		for (final IDescription child : output.getOwnChildren()) {
			if (!isDeclaredHere(child, output)) continue;
			if ("display".equals(child.getKeyword()) && owner instanceof EGUIExperiment) {
				final EDisplay display = factory.createEDisplay();
				display.setName(child.getName());
				display.setDefineGamlCode(true);
				display.setGamlCode(body(child));
				copyFacets(child, display, Set.of("name"));
				model.getObjects().add(display);
				final EDisplayLink link = factory.createEDisplayLink();
				link.setExperiment((EGUIExperiment) owner);
				link.setDisplay(display);
				owner.getDisplayLinks().add(link);
				display.setDisplayLink(link);
				connect(link, owner, display);
			} else if ("monitor".equals(child.getKeyword())) {
				final EMonitor monitor = factory.createEMonitor();
				monitor.setName(child.getName());
				monitor.setValue(facet(child, "value"));
				owner.getMonitors().add(monitor);
				model.getObjects().add(monitor);
			}
		}
	}

	private void addParameter(final IDescription description, final EExperiment owner) {
		final EParameter parameter = factory.createEParameter();
		parameter.setName(description instanceof IVariableDescription
				? ((IVariableDescription) description).getParameterName() : description.getName());
		parameter.setVariable(description.hasFacet("var") ? facet(description, "var") : description.getName());
		parameter.setInit(facet(description, "init"));
		parameter.setMin(facet(description, "min"));
		parameter.setMax(facet(description, "max"));
		parameter.setStep(facet(description, "step"));
		parameter.setAmong(facet(description, "among"));
		parameter.setCategory(description.hasFacet("category") ? description.getLitteral("category") : "");
		owner.getParameters().add(parameter);
		model.getObjects().add(parameter);
	}

	private void connectInheritance() {
		for (final Map.Entry<ISpeciesDescription, ESpecies> entry : species.entrySet()) {
			final ISpeciesDescription description = entry.getKey();
			if (!description.hasFacet("parent")) continue;
			final ESpecies parent = species.get(description.getParent());
			if (parent != null && parent != entry.getValue()) {
				final EInheritLink link = factory.createEInheritLink();
				link.setParent(parent);
				link.setChild(entry.getValue());
				parent.getInheritingLinks().add(link);
				entry.getValue().setInheritsFrom(parent);
				connect(link, parent, entry.getValue());
			} else {
				// Keep an explicit external/built-in parent without adding a built-in node.
				final EFacet facet = factory.createEFacet();
				facet.setName("parent");
				facet.setValue(facet(description, "parent"));
				entry.getValue().getFacets().add(facet);
			}
		}
	}

	private void connect(final EGamaLink link, final EGamaObject source, final EGamaObject target) {
		link.setSource(source);
		link.setTarget(target);
		model.getLinks().add(link);
	}

	private void copyFacets(final IDescription description, final EGamaObject target, final Set<String> excluded) {
		final EObject source = description.getUnderlyingElement();
		final boolean sourceBacked = source instanceof FacetsAndBlock
				&& NodeModelUtils.findActualNodeFor(source) != null;
		description.getFacets().forEach((name, value) -> {
			if (!name.startsWith("_") && !"internal".equals(name) && !excluded.contains(name)) {
				final String sourceValue = sourceFacet(source, name);
				// Validation adds defaults such as unquoted display/experiment titles that are not declarations.
				if (sourceBacked && sourceValue == null) return;
				final EFacet facet = factory.createEFacet();
				facet.setName(name);
				facet.setValue(sourceValue == null ? value.serializeToGaml(false) : sourceValue);
				target.getFacets().add(facet);
			}
		});
	}

	private static String facet(final IDescription description, final String name) {
		if (!description.hasFacet(name)) return "";
		final String source = sourceFacet(description.getUnderlyingElement(), name);
		return source == null ? description.getFacet(name).serializeToGaml(false) : source;
	}

	private static String body(final IDescription description) {
		final String source = sourceBody(description.getUnderlyingElement());
		if (source != null) return source;
		final StringBuilder body = new StringBuilder();
		for (final IDescription child : description.getOwnChildren()) {
			if (!"arg".equals(child.getKeyword()) && !child.isBuiltIn()) {
				body.append(child.getSerializer().serialize(child, false)).append(System.lineSeparator());
			}
		}
		return body.toString();
	}

	static String sourceFacet(final EObject declaration, final String name) {
		if (!(declaration instanceof FacetsAndBlock statement)) return null;
		for (final var facet : statement.getFacets()) {
			final String key = switch (facet.getKey()) {
				case "<-" -> "init";
				case "->" -> "function";
				default -> facet.getKey().replace(":", "");
			};
			if (!name.equals(key) || facet.getExpr() == null) continue;
			final var node = NodeModelUtils.findActualNodeFor(facet.getExpr());
			if (node == null) return null;
			final var text = new StringBuilder();
			for (final var leaf : node.getLeafNodes()) {
				// Expressions may move into a single-line header; comments must not swallow subsequent facets.
				text.append(leaf.isHidden() && !leaf.getText().isBlank() ? " " : leaf.getText());
			}
			return text.toString().trim();
		}
		return null;
	}

	static String sourceBody(final EObject declaration) {
		if (!(declaration instanceof FacetsAndBlock statement)
				|| NodeModelUtils.findActualNodeFor(declaration) == null) return null;
		final Block block = statement.getBlock();
		// Equations use their own assignment list, not the normal statement Block.
		if (block == null && !(declaration instanceof S_Equations)) return "";
		final var node = NodeModelUtils.findActualNodeFor(block == null ? declaration : block);
		if (node == null) return null;
		int start = -1;
		int end = -1;
		for (final var leaf : node.getLeafNodes()) {
			if (leaf.isHidden()) continue;
			if (start < 0 && "{".equals(leaf.getText())) start = leaf.getEndOffset();
			if ("}".equals(leaf.getText())) end = leaf.getOffset();
		}
		if (start < 0 || end < start) return "";
		return node.getText().substring(start - node.getTotalOffset(), end - node.getTotalOffset());
	}
}
