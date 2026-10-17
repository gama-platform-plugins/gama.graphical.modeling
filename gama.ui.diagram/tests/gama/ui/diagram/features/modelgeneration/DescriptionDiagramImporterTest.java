package gama.ui.diagram.features.modelgeneration;

import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.parser.IParser;

import gama.api.compilation.descriptions.IActionDescription;
import gama.api.compilation.descriptions.IDescription;
import gama.api.compilation.descriptions.IExperimentDescription;
import gama.api.compilation.descriptions.IModelDescription;
import gama.api.compilation.descriptions.ISpeciesDescription;
import gama.api.compilation.descriptions.IStatementDescription;
import gama.api.compilation.descriptions.IVariableDescription;
import gama.api.compilation.serialization.ISymbolSerializer;
import gama.api.gaml.expressions.IExpressionDescription;
import gama.api.gaml.symbols.Facets;
import gama.api.gaml.types.IType;
import gama.ui.diagram.metamodel.EAction;
import gama.ui.diagram.metamodel.EBatchExperiment;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EGamaLink;
import gama.ui.diagram.metamodel.EGamaModel;
import gama.ui.diagram.metamodel.EGrid;
import gama.ui.diagram.metamodel.EInheritLink;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EVariable;
import gama.ui.diagram.metamodel.GamaPackage;
import gaml.compiler.GamlStandaloneSetup;
import gaml.compiler.gaml.ActionArguments;
import gaml.compiler.gaml.GamlDefinition;
import gaml.compiler.gaml.Model;
import gaml.compiler.gaml.S_Action;
import gaml.compiler.gaml.S_Definition;
import gaml.compiler.gaml.S_Display;
import gaml.compiler.gaml.S_Experiment;
import gaml.compiler.gaml.S_Global;
import gaml.compiler.gaml.S_Species;
import gaml.compiler.gaml.Statement;

/**
 * Standalone regression checks; use the same target-platform classpath as the plugin.
 * The strict description doubles reject any access to compiled symbols.
 */
public final class DescriptionDiagramImporterTest {

	private static final class Description implements InvocationHandler {
		final IDescription value;
		final String keyword;
		final String name;
		final List<IDescription> children = new ArrayList<>();
		final List<IDescription> arguments = new ArrayList<>();
		final List<IExperimentDescription> experiments = new ArrayList<>();
		final Map<String, Description> inheritedAttributes = new LinkedHashMap<>();
		final Map<String, Description> inheritedActions = new LinkedHashMap<>();
		final Map<String, Description> inheritedSpecies = new LinkedHashMap<>();
		final Facets facets = new Facets();
		IDescription owner;
		ISpeciesDescription parent;
		EObject source = EcoreFactory.eINSTANCE.createEClass();
		boolean builtIn;
		boolean synthetic;
		boolean batch;
		String type = "int";
		String code;
		String parameterName;

		Description(final Class<? extends IDescription> kind, final String keyword, final String name) {
			this.keyword = keyword;
			this.name = name;
			value = (IDescription) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { kind }, this);
		}

		Description add(final Description child) {
			child.owner = value;
			children.add(child.value);
			return child;
		}

		Description facet(final String key, final String text) {
			final IExpressionDescription expression = (IExpressionDescription) Proxy.newProxyInstance(
					getClass().getClassLoader(), new Class<?>[] { IExpressionDescription.class }, (p, m, a) -> {
						return switch (m.getName()) {
							case "serializeToGaml" -> text;
							case "getStrings" -> List.of("moving");
							default -> throw new AssertionError("Unexpected expression call: " + m.getName());
						};
					});
			facets.put(key, expression);
			return this;
		}

		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) {
			return switch (method.getName()) {
				case "getKeyword" -> keyword;
				case "getName" -> name;
				case "isBuiltIn" -> builtIn;
				case "isSynthetic" -> synthetic;
				case "isParameter" -> "parameter".equals(keyword) || facets.containsKey("parameter");
				case "getParameterName" -> parameterName == null ? name : parameterName;
				case "getLitteral" -> facets.get((String) args[0]).serializeToGaml(false).replace("\"", "");
				case "isVoid" -> !facets.containsKey("type");
				case "getUnderlyingElement" -> source;
				case "getEnclosingDescription" -> owner;
				case "getOwnChildren" -> children;
				case "getExperiments" -> experiments;
				case "getParent" -> parent;
				case "getAttribute" -> lookup(inheritedAttributes, args[0]);
				case "getAction" -> lookup(inheritedActions, args[0]);
				case "getMicroSpecies" -> lookup(inheritedSpecies, args[0]);
				case "getFacets" -> facets;
				case "hasFacet" -> facets.containsKey((String) args[0]);
				case "getFacet" -> facets.get((String) args[0]);
				case "isGrid" -> "grid".equals(keyword);
				case "isBatch" -> batch;
				case "getFormalArgs" -> arguments;
				case "getGamlType" -> Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IType.class },
						(p, m, a) -> {
							if ("serializeToGaml".equals(m.getName())) return type;
							throw new AssertionError("Unexpected type call: " + m.getName());
						});
				case "getSerializer" -> new ISymbolSerializer() {
					@Override
					public String serialize(final IDescription description, final boolean includingBuiltIn) {
						if (code == null) throw new AssertionError("Missing statement fixture: " + name);
						return code;
					}
				};
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				case "toString" -> keyword + " " + name;
				default -> throw new AssertionError("Unexpected description call: " + method.getName());
			};
		}

		private IDescription lookup(final Map<String, Description> map, final Object name) {
			final Description result = map.get(name);
			return result == null ? null : result.value;
		}
	}

	private static Description statement(final String keyword, final String name) {
		return new Description("action".equals(keyword) ? IActionDescription.class : IStatementDescription.class,
				keyword, name);
	}

	private static Description variable(final String name) {
		return new Description(IVariableDescription.class, "int", name);
	}

	private static Description species(final String name) {
		return new Description(ISpeciesDescription.class, "species", name);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static ESpecies findSpecies(final EGamaModel model, final String name) {
		return model.getObjects().stream().filter(object -> object instanceof ESpecies && name.equals(object.getName()))
				.map(object -> (ESpecies) object).findFirst().orElseThrow();
	}

	private static void builtInsAndOverrides() {
		final Description model = new Description(IModelDescription.class, "model", "builtins");
		final Description agent = model.add(species("agent"));
		agent.builtIn = true;
		final Description builtInParent = species("builtin_agent");
		final Description user = model.add(species("user"));
		user.parent = (ISpeciesDescription) builtInParent.value;
		for (final String name : List.of("name", "shape", "location", "color", "seed", "cycle")) {
			final Description inherited = variable(name);
			inherited.builtIn = true;
			inherited.source = null;
			builtInParent.inheritedAttributes.put(name, inherited);
			final Description copy = user.add(variable(name));
			copy.builtIn = true;
			copy.source = null;
		}
		// Replace the copied built-ins with explicit source declarations using the exact same names.
		user.children.removeIf(child -> List.of("name", "shape", "seed").contains(child.getName()));
		user.add(variable("name")).type = "string";
		user.add(variable("shape")).type = "geometry";
		user.add(variable("seed")).facet("init", "42").facet("parameter", "\"Seed\"");
		final Description implicit = user.add(variable("generated_population"));
		implicit.synthetic = true;
		final Description noSource = user.add(variable("compiler_generated"));
		noSource.source = null;
		final Description die = statement("action", "die");
		die.builtIn = true;
		die.source = null;
		builtInParent.inheritedActions.put("die", die);
		final Description override = user.add(statement("action", "die"));
		final Description write = override.add(statement("write", "body"));
		write.code = "write \"explicit override\";";
		final Description builtin = user.add(statement("action", "builtin_action"));
		builtin.builtIn = true;
		final EGamaModel imported = DescriptionDiagramImporter.importModel((IModelDescription) model.value);
		final ESpecies result = findSpecies(imported, "user");
		check(imported.getObjects().stream().noneMatch(object -> "agent".equals(object.getName())), "Built-in species");
		check(result.getVariables().stream().map(EVariable::getName).toList().equals(List.of("name", "shape", "seed")),
				"Explicit overrides missing or built-in/synthetic attributes leaked");
		check(result.getVariables().get(2).getInit().equals("42"), "Override initializer lost");
		check(result.getActionLinks().size() == 1, "Built-in action leaked or override hidden");
		check(result.getActionLinks().get(0).getAction().getName().equals("die"), "Redefined built-in action hidden");
		check(result.getActionLinks().get(0).getAction().getGamlCode().contains("explicit override"), "Body lost");
	}

	private static void hierarchyAndProvenance() {
		final Description model = new Description(IModelDescription.class, "model", "hierarchy");
		final Description parent = model.add(species("parent"));
		final Description inherited = parent.add(variable("energy"));
		final Description parentAction = parent.add(statement("action", "move"));
		final Description inheritedNested = parent.add(species("inherited_nested"));
		final Description child = model.add(species("child")).facet("parent", "parent");
		model.children.remove(child.value);
		model.children.add(0, child.value);
		child.parent = (ISpeciesDescription) parent.value;
		parent.inheritedAttributes.put("energy", inherited);
		parent.inheritedActions.put("move", parentAction);
		parent.inheritedSpecies.put("inherited_nested", inheritedNested);
		// Reparented compiler copies still point to the original source declarations.
		final Description copy = child.add(variable("energy"));
		copy.source = inherited.source;
		final Description actionCopy = child.add(statement("action", "move"));
		actionCopy.source = parentAction.source;
		final Description nestedCopy = child.add(species("inherited_nested"));
		nestedCopy.source = inheritedNested.source;
		child.children.add(parentAction.value);
		final Description override = child.add(variable("shape"));
		override.type = "geometry";
		final Description nested = child.add(species("nested"));
		nested.add(species("grandchild"));
		final Description grid = model.add(new Description(ISpeciesDescription.class, "grid", "cells"));
		grid.facet("width", "20");
		final Description imported = model.add(species("from_import"));
		imported.add(variable("local_to_import"));
		final EGamaModel result = DescriptionDiagramImporter.importModel((IModelDescription) model.value);
		final ESpecies childNode = findSpecies(result, "child");
		check(childNode.getVariables().stream().map(EVariable::getName).toList().equals(List.of("shape")),
				"Inherited attribute copy duplicated");
		check(childNode.getActionLinks().isEmpty(), "Inherited action duplicated");
		check(childNode.getMicroSpeciesLinks().size() == 1, "Inherited microspecies duplicated");
		check(childNode.getInheritsFrom() == findSpecies(result, "parent"), "Inheritance not resolved");
		check(childNode.getMicroSpeciesLinks().get(0).getMicro() == findSpecies(result, "nested"), "Macro hierarchy");
		check(findSpecies(result, "nested").getMicroSpeciesLinks().get(0).getMicro()
				== findSpecies(result, "grandchild"), "Recursive microspecies missing");
		check(findSpecies(result, "cells") instanceof EGrid, "Grid missing");
		check(findSpecies(result, "from_import").getVariables().size() == 1, "Imported declarations discarded");
		check(result.getLinks().stream().filter(link -> link instanceof EInheritLink).count() == 1, "Inheritance link");
		final String speciesCode = ModelGenerator.defineSpecies(childNode, 0, null);
		check(speciesCode.contains("parent:parent") && speciesCode.contains("species nested")
				&& speciesCode.contains("species grandchild") && speciesCode.contains("geometry shape;"),
				"Species body or hierarchy lost when regenerating GAML");
		final var resource = new org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl(
				org.eclipse.emf.common.util.URI.createURI("memory:/import.gadl"));
		resource.getContents().add(result);
		try {
			final var bytes = new java.io.ByteArrayOutputStream();
			resource.save(bytes, Map.of());
			final var loaded = new org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl();
			loaded.load(new java.io.ByteArrayInputStream(bytes.toByteArray()), Map.of());
			final EGamaModel reloaded = (EGamaModel) loaded.getContents().get(0);
			check(findSpecies(reloaded, "child").getInheritsFrom() == findSpecies(reloaded, "parent"), "Persisted parent");
			check(findSpecies(reloaded, "child").getMicroSpeciesLinks().get(0).getMicro()
					== findSpecies(reloaded, "nested"), "Persisted nested species");
		} catch (final java.io.IOException error) {
			throw new AssertionError(error);
		}
	}

	private static void componentsAndExperiments() {
		final Description model = new Description(IModelDescription.class, "model", "components");
		final Description agent = model.add(species("agent_with_overrides"));
		agent.facet("skills", "[moving]").facet("control", "fsm");
		final Description init = agent.add(statement("init", "init"));
		init.add(statement("write", "init_body")).code = "write \"{init}\";";
		for (final String kind : List.of("reflex", "state", "task", "plan", "perceive", "rule", "equation", "aspect")) {
			final Description component = agent.add(statement(kind, kind + "_name"));
			component.facet("when", "true");
			component.add(statement("write", kind + "_body")).code = "write \"a { brace } and \\\\ path\";";
		}
		final Description action = agent.add(statement("action", "calculate")).facet("type", "float");
		final Description argument = variable("amount").facet("init", "2");
		argument.type = "float";
		action.arguments.add(argument.value);
		action.add(statement("return", "result")).code = "return amount * 2;";
		final Description gui = new Description(IExperimentDescription.class, "experiment", "gui");
		gui.owner = model.value;
		model.experiments.add((IExperimentDescription) gui.value);
		gui.add(variable("experiment_counter")).facet("init", "0");
		final Description experimentAction = gui.add(statement("action", "reset"));
		experimentAction.add(statement("write", "reset_body")).code = "write \"reset counter\";";
		final Description parameter = gui.add(new Description(IVariableDescription.class, "parameter", "Population"))
				.facet("var", "population").facet("min", "1").facet("max", "100").facet("category", "\"Inputs\"");
		parameter.parameterName = "Initial population";
		final Description output = gui.add(statement("output", "output"));
		final Description display = output.add(statement("display", "view")).facet("type", "2d");
		display.add(statement("species", "drawing")).code = "species agent_with_overrides aspect:aspect_name;";
		output.add(statement("monitor", "Count")).facet("value", "length(agent_with_overrides)");
		final Description batch = new Description(IExperimentDescription.class, "experiment", "batch");
		batch.owner = model.value;
		batch.batch = true;
		model.experiments.add((IExperimentDescription) batch.value);
		batch.add(statement("output", "batch_output")).add(statement("monitor", "Batch count")).facet("value", "1");
		final EGamaModel result = DescriptionDiagramImporter.importModel((IModelDescription) model.value);
		final ESpecies node = findSpecies(result, "agent_with_overrides");
		check(node.getSkills().equals(List.of("moving")), "Explicit skill lost");
		check(node.getFacets().stream().anyMatch(f -> "control".equals(f.getName()) && "fsm".equals(f.getValue())),
				"Control facet lost");
		check(node.getInit().contains("\"{init}\""), "Init/body parsing corrupts braces");
		check(node.getActionLinks().size() == 1 && node.getReflexLinks().size() == 1 && node.getAspectLinks().size() == 1
				&& node.getStateLinks().size() == 1 && node.getTaskLinks().size() == 1 && node.getPlanLinks().size() == 1
				&& node.getPerceiveLinks().size() == 1 && node.getRuleLinks().size() == 1
				&& node.getEquationLinks().size() == 1, "Missing component relation");
		final EAction actionNode = node.getActionLinks().get(0).getAction();
		check("float".equals(actionNode.getReturnType()) && actionNode.getVariables().size() == 1
				&& "2".equals(actionNode.getVariables().get(0).getInit()), "Action signature/default lost");
		check(actionNode.getGamlCode().contains("return amount * 2;"), "Action body lost");
		check(node.getRuleLinks().get(0).getRule().getGamlCode().contains("{ brace }"), "Rule body lost");
		check(ModelGenerator.defineGeneric(node.getRuleLinks().get(0), 1).contains("{ brace }"),
				"Rule body lost when regenerating GAML");
		check(node.getReflexList().equals(List.of("reflex_name")), "Reflex execution order not preserved");
		final EExperiment experiment = (EExperiment) findSpecies(result, "gui");
		check(experiment.getParameters().size() == 1 && experiment.getVariables().size() == 1
				&& "experiment_counter".equals(experiment.getVariables().get(0).getName()), "Parameter shown as attribute");
		check("population".equals(experiment.getParameters().get(0).getVariable()), "Parameter binding lost");
		check("Initial population".equals(experiment.getParameters().get(0).getName()), "Parameter label lost");
		check(ModelGenerator.defineParameter(experiment.getParameters().get(0)).contains("category: \"Inputs\""),
				"Parameter category double-quoted when regenerating GAML");
		check(experiment.getMonitors().size() == 1 && experiment.getDisplayLinks().size() == 1, "Output declarations lost");
		check(experiment.getDisplayLinks().get(0).getDisplay().getGamlCode().contains("aspect:aspect_name"), "Display body");
		final String experimentCode = ModelGenerator.defineExperiment(experiment, null);
		check(experimentCode.contains("int experiment_counter <- 0;") && experimentCode.contains("reset counter"),
				"Experiment's own attributes/actions lost when regenerating GAML");
		check(findSpecies(result, "batch") instanceof EBatchExperiment, "Batch experiment type lost");
		check(ModelGenerator.defineExperiment((EExperiment) findSpecies(result, "batch"), null).contains("Batch count"),
				"Batch monitor lost when regenerating GAML");
		for (final EGamaLink link : result.getLinks()) {
			check(link.getSource() != null && link.getTarget() != null && link.getModel() == result, "Unwired relation");
		}
		final var resource = new org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl(
				org.eclipse.emf.common.util.URI.createURI("memory:/experiments.gadl"));
		resource.getContents().add(result);
		try {
			final var bytes = new java.io.ByteArrayOutputStream();
			resource.save(bytes, Map.of());
			final var loaded = new org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl();
			loaded.load(new java.io.ByteArrayInputStream(bytes.toByteArray()), Map.of());
			final EGamaModel reloaded = (EGamaModel) loaded.getContents().get(0);
			final EExperiment reloadedGui = (EExperiment) findSpecies(reloaded, "gui");
			final var savedParameter = reloadedGui.getParameters().get(0);
			check(savedParameter.eResource() == loaded && !savedParameter.eIsProxy(), "Parameter not persisted");
			check("population".equals(savedParameter.getVariable()) && "Inputs".equals(savedParameter.getCategory())
					&& "Initial population".equals(savedParameter.getName()), "Parameter values lost on save");
			check(reloadedGui.getMonitors().get(0).eResource() == loaded
					&& "length(agent_with_overrides)".equals(reloadedGui.getMonitors().get(0).getValue()),
					"GUI monitor not persisted");
			final EExperiment reloadedBatch = (EExperiment) findSpecies(reloaded, "batch");
			check(reloadedBatch.getMonitors().get(0).eResource() == loaded
					&& "1".equals(reloadedBatch.getMonitors().get(0).getValue()), "Batch monitor not persisted");
		} catch (final java.io.IOException error) {
			throw new AssertionError(error);
		}
	}

	/** Real parser nodes with deliberately empty post-validation bodies and unusable serializers. */
	public static EGamaModel importSource(final IParser parser, final String text) {
		final var parsed = parser.parse(new StringReader(text));
		for (final var error : parsed.getSyntaxErrors()) {
			throw new AssertionError("Source fixture line " + error.getStartLine() + ": "
					+ error.getSyntaxErrorMessage().getMessage() + " [" + error.getText() + "]");
		}
		final var ast = (Model) parsed.getRootASTElement();
		final var model = new Description(IModelDescription.class, "model", ast.getName());
		final var locals = new LinkedHashMap<String, Description>();
		for (final var statement : ast.getBlock().getStatements()) {
			if (statement instanceof S_Global global) {
				model.source = global;
				sourceFacets(model, global);
				for (final var child : global.getBlock().getStatements()) model.add(sourceDescription(child, locals));
			} else {
				model.add(sourceDescription(statement, locals));
			}
		}
		for (final var declaration : locals.values()) {
			if (declaration.facets.containsKey("parent")) {
				final String name = DescriptionDiagramImporter.sourceFacet(declaration.source, "parent");
				final Description parent = locals.get(name);
				if (parent != null) declaration.parent = (ISpeciesDescription) parent.value;
			}
		}
		return DescriptionDiagramImporter.importModel((IModelDescription) model.value);
	}

	private static Description sourceDescription(final Statement statement, final Map<String, Description> locals) {
		final String keyword = statement instanceof S_Definition definition && !(statement instanceof S_Action)
				? NodeModelUtils.getTokenText(NodeModelUtils.findActualNodeFor(definition.getTkey()))
				: statement.getKey();
		final String name = statement instanceof S_Display display ? display.getName().replaceAll("^['\"]|['\"]$", "")
				: statement instanceof GamlDefinition definition ? definition.getName()
				: statement.getExpr() == null ? "" : NodeModelUtils.getTokenText(
						NodeModelUtils.findActualNodeFor(statement.getExpr())).replaceAll("^['\"]|['\"]$", "");
		final Class<? extends IDescription> kind = statement instanceof S_Experiment ? IExperimentDescription.class
				: statement instanceof S_Species ? ISpeciesDescription.class
				: statement instanceof S_Action ? IActionDescription.class
				: statement instanceof S_Definition ? IVariableDescription.class : IStatementDescription.class;
		final var description = new Description(kind, keyword, name);
		description.source = statement;
		description.type = statement instanceof S_Definition ? keyword : "int";
		sourceFacets(description, statement);
		if (statement instanceof S_Species) locals.put(name, description);
		if (statement instanceof ActionArguments callable) {
			for (final var argument : callable.getArgs()) {
				final var parameter = variable(argument.getName());
				parameter.type = NodeModelUtils.getTokenText(NodeModelUtils.findActualNodeFor(argument.getType()));
				if (argument.getDefault() != null) parameter.facet("init",
						NodeModelUtils.getTokenText(NodeModelUtils.findActualNodeFor(argument.getDefault())));
				description.arguments.add(parameter.value);
			}
		}
		if ((statement instanceof S_Species || statement instanceof S_Experiment || "output".equals(keyword))
				&& statement.getBlock() != null) {
			for (final var child : statement.getBlock().getStatements()) {
				description.add(sourceDescription(child, locals));
			}
		}
		return description;
	}

	private static void sourceFacets(final Description description, final Statement statement) {
		for (final var facet : statement.getFacets()) {
			final String key = switch (facet.getKey()) {
				case "<-" -> "init";
				case "->" -> "function";
				default -> facet.getKey().replace(":", "");
			};
			if (facet.getExpr() != null) description.facet(key, "category".equals(key)
					? NodeModelUtils.getTokenText(NodeModelUtils.findActualNodeFor(facet.getExpr()))
					: "BROKEN_COMPILED_SERIALIZATION");
		}
		if ((statement instanceof S_Experiment || statement instanceof S_Display)
				&& !description.facets.containsKey("title")) {
			description.facet("title", statement instanceof S_Experiment
					? "Experiment " + description.name : description.name);
		}
	}

	private static void sourcePreservation() {
		final var parser = GamlStandaloneSetup.doSetup().getInstance(IParser.class);
		final String source = """
				model original
				global {
					float rate <- 2 /* keep multiplication infix */ * 3;
					float computed function: rate * 2;
					init {
						// inter-statement comment and braces in strings must survive
						write "{ /* not a comment */ }";
						rate <- rate * 4 * 5;
						do calculate();
					}
					action calculate() { write "unchanged"; }
					reflex generation { do calculate(); }
				}
				species worker frequency: (2 /* inline comment */ * 3) {
					init { do calculate(); }
					action calculate() { write "worker"; }
					reflex step { do calculate(); }
					float t;
					float S <- 10.0;
					float I <- 1.0;
					float R <- 0.0;
					equation SIR {
						// Keep the independent time variable and all differential equations.
						diff(S, t) = (-S * I);
						diff(I, t) = (S * I) - I;
						diff(R, t) = I;
					}
					reflex solving { solve SIR method: "rk4" step_size: 0.01; }
				}
				experiment "multi word experiment" type: gui {
					parameter "Number of Susceptible" var: rate;
					output {
						display "sir display" {
							graphics "cell" { draw circle(1); }
						}
						display "custom display" title: "Custom title" {}
					}
				}
				""";
		final var imported = importSource(parser, source);
		final var world = findSpecies(imported, "world");
		check(world.getVariables().get(0).getInit().equals("2   * 3"), "Initializer uses compiled expression");
		check(world.getInit().contains("rate <- rate * 4 * 5;") && world.getInit().contains("do calculate();")
				&& world.getInit().contains("// inter-statement"), "Raw init body lost");
		check(world.getReflexLinks().get(0).getReflex().getGamlCode().contains("do calculate();"),
				"Validated reflex children can be empty without losing the source body");
		final var worker = findSpecies(imported, "worker");
		check(worker.getFacets().get(0).getValue().equals("(2   * 3)"), "Facet uses compiled expression");
		check(worker.getActionLinks().get(0).getAction().getGamlCode().contains("write \"worker\";"),
				"Raw action body lost");
		final var equations = worker.getEquationLinks().get(0).getEquation();
		check(equations.getGamlCode().contains("diff(S, t) = (-S * I);")
				&& equations.getGamlCode().contains("diff(I, t) = (S * I) - I;")
				&& equations.getGamlCode().contains("diff(R, t) = I;")
				&& equations.getGamlCode().contains("// Keep the independent"), "Equation AST body lost");
		check(EcoreUtil.copy(equations).getGamlCode().equals(equations.getGamlCode()),
				"Equation body lost during preview copying");
		final String generated = "model original\n" + ModelGenerator.defineSpecies(world, 0, null)
				+ ModelGenerator.defineSpecies(worker, 0, null);
		for (final var error : parser.parse(new StringReader(generated)).getSyntaxErrors()) {
			throw new AssertionError("Generated fixture line " + error.getStartLine() + ": "
					+ error.getSyntaxErrorMessage().getMessage() + "\n" + generated);
		}
		final var experiment = (EExperiment) findSpecies(imported, "multi word experiment");
		check(experiment.getFacets().stream().noneMatch(facet -> "title".equals(facet.getName())),
				"Compiler-injected experiment title leaked into diagram");
		final var display = experiment.getDisplayLinks().get(0).getDisplay();
		check(display.getFacets().stream().noneMatch(facet -> "title".equals(facet.getName())),
				"Compiler-injected display title leaked into diagram");
		final var explicit = experiment.getDisplayLinks().get(1).getDisplay();
		check(explicit.getFacets().stream().anyMatch(facet -> "title".equals(facet.getName())
				&& "\"Custom title\"".equals(facet.getValue())), "Explicit title expression lost");
		for (final var error : parser.parse(new StringReader("model original\nglobal {}\n"
				+ ModelGenerator.defineExperiment(experiment, null))).getSyntaxErrors()) {
			throw new AssertionError("Generated parameter/display line " + error.getStartLine() + ": "
					+ error.getSyntaxErrorMessage().getMessage());
		}
	}

	private static void storedAttributesAreCopied() {
		int checked = 0;
		for (final var classifier : GamaPackage.eINSTANCE.getEClassifiers()) {
			if (!(classifier instanceof EClass type) || type.isAbstract()) continue;
			final var object = EcoreUtil.create(type);
			for (final var attribute : type.getEAllAttributes()) {
				if (!List.of("gamlCode", "shapeType", "isColorCst", "textSize", "imageSize")
						.contains(attribute.getName())) continue;
				check(!attribute.isDerived(), type.getName() + "." + attribute.getName() + " is stored, not derived");
				final Object value = attribute.getEAttributeType().getInstanceClass() == String.class
						? "source-body" : Boolean.TRUE;
				object.eSet(attribute, value);
				check(value.equals(EcoreUtil.copy(object).eGet(attribute)), "Preview copy lost " + attribute.getName());
				checked++;
			}
		}
		check(checked == 17, "Missing stored code/layer attributes: " + checked);
	}

	public static void main(final String[] args) {
		builtInsAndOverrides();
		hierarchyAndProvenance();
		componentsAndExperiments();
		sourcePreservation();
		storedAttributesAreCopied();
		System.out.println("PASS: description-only import, built-in exclusion and explicit overrides, inherited copies, "
				+ "nested species, inheritance, imports, component bodies/facets, action signatures, experiments, "
				+ "outputs, parameters and XMI persistence");
	}
}
