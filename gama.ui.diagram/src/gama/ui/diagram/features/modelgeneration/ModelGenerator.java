/*******************************************************************************************************
 *
 * ModelGenerator.java, in gama.ui.diagram, is part of the source code of the GAMA modeling and simulation
 * platform (v.1.9.3).
 *
 * (c) 2007-2024 UMI 209 UMMISCO IRD/SU & Partners (IRIT, MIAT, TLU, CTU)
 *
 * Visit https://github.com/gama-platform/gama for license information and contacts.
 *
 ********************************************************************************************************/

package gama.ui.diagram.features.modelgeneration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.Shape;
import gama.api.kernel.species.IModelSpecies;
import org.eclipse.xtext.resource.SynchronizedXtextResourceSet;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.common.collect.Iterables;

import gama.api.compilation.GamlCompilationError;
import gama.api.compilation.descriptions.IExperimentDescription;
import gama.api.compilation.descriptions.IModelDescription;
import gama.api.constants.IGamlIssue;
import gama.ui.diagram.editor.GamaDiagramEditor;
import gama.ui.diagram.editor.ModelStructure;
import gama.ui.diagram.features.ExampleUtil;
import gama.ui.diagram.metamodel.EAction;
import gama.ui.diagram.metamodel.EActionLink;
import gama.ui.diagram.metamodel.EAspect;
import gama.ui.diagram.metamodel.EAspectLink;
import gama.ui.diagram.metamodel.EChartLayer;
import gama.ui.diagram.metamodel.EDisplay;
import gama.ui.diagram.metamodel.EDisplayLink;
import gama.ui.diagram.metamodel.EEquation;
import gama.ui.diagram.metamodel.EEquationLink;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EExperimentLink;
import gama.ui.diagram.metamodel.EFacet;
import gama.ui.diagram.metamodel.EGamaLink;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.EGrid;
import gama.ui.diagram.metamodel.ELayer;
import gama.ui.diagram.metamodel.ELayerAspect;
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
import gama.ui.diagram.metamodel.EClassSpecies;
import gama.ui.diagram.metamodel.EState;
import gama.ui.diagram.metamodel.EStateLink;
import gama.ui.diagram.metamodel.ESubSpeciesLink;
import gama.ui.diagram.metamodel.ETask;
import gama.ui.diagram.metamodel.ETaskLink;
import gama.ui.diagram.metamodel.EVariable;
import gama.ui.diagram.metamodel.EWorldAgent;
import gaml.compiler.resource.GamlResource;
import gaml.compiler.validation.IGamlBuilderListener;

/**
 * The Class ModelGenerator.
 */
public class ModelGenerator {

	/** The el. */
	private static String EL = System.lineSeparator();

	/**
	 * Builds the model description.
	 *
	 * @param r
	 *            the r
	 * @param errors
	 *            the errors
	 * @return the model description
	 */
	private static IModelDescription buildModelDescription(final GamlResource r,
			final List<GamlCompilationError> errors) {
		try {

			// Syntactic errors detected, we cannot build the resource
			if (r.hasErrors()) {
				if (errors != null) {
					// Create a compilation error using the new GamlCompilationError API
					// Use the factory to associate the error with the EObject source and mark it as Error
					errors.add(GamlCompilationError.create("Syntax errors detected ", IGamlIssue.GENERAL,
							r.getContents().get(0), GamlCompilationError.Type.Error));
				}
				return null;
			}
			// We build the description
			final IModelDescription model = r.buildCompleteDescription();
			if (errors != null) { Iterables.addAll(errors, r.getValidationContext()); }
			return model;
		} finally {}
	}

	/**
	 * Model generation.
	 *
	 * @param fp
	 *            the fp
	 * @param diagram
	 *            the diagram
	 * @return the i model
	 */
	public static IModelSpecies modelGeneration(final IFeatureProvider fp, final Diagram diagram) {
		final GamaDiagramEditor diagramEditor = (GamaDiagramEditor) ExampleUtil.getDiagramEditor(fp);
		diagramEditor.initIdsEObjects();
		final XtextResourceSet rs = new SynchronizedXtextResourceSet();
		rs.setClasspathURIContext(ModelGenerator.class);
		URI du = diagramEditor.getDiagramEditorInput().getUri();
		final URI uri = URI.createURI(du.trimFragment().toString().replace(".gadl", ".gaml"), true);
		final GamlResource resource = (GamlResource) rs.createResource(uri);
		final String gamlModel = ModelGenerator.generateModel(fp, diagram, null);
		final InputStream is = new ByteArrayInputStream(gamlModel.getBytes());
		diagramEditor.setResource(resource);
		try {
			resource.load(is, null);
		} catch (final IOException e1) {
			e1.printStackTrace();
		}
		try {
			final Set<GamlResource> resources = new HashSet<>();
			resources.add(resource);
			final IModelDescription modeldesc = buildModelDescription(resource, new ArrayList<>());
			return modeldesc == null ? null : (IModelSpecies) modeldesc.compile();
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Gets the path.
	 *
	 * @param fp
	 *            the fp
	 * @param diagram
	 *            the diagram
	 * @return the path
	 */
	public static String getPath(final IFeatureProvider fp, final Diagram diagram) {
		final List<Shape> contents = diagram.getChildren();
		URI uri = null;
		if (contents == null) return "";
		uri = EcoreUtil.getURI((EObject) fp.getBusinessObjectForPictogramElement(contents.get(0)));
		uri = uri.trimFragment();
		if (uri.isPlatform()) { uri = URI.createURI(uri.toPlatformString(true)); }
		// String containerStr = "/"+ uri.segment(0);
		String path = ResourcesPlugin.getWorkspace().getRoot().getLocation() + uri.path();
		path = path.replace(".gadl", ".gaml");
		return path;
	}

	/**
	 * Gets the folder.
	 *
	 * @param fp
	 *            the fp
	 * @param diagram
	 *            the diagram
	 * @return the folder
	 */
	public static String getFolder(final IFeatureProvider fp, final Diagram diagram) {
		final String path = getPath(fp, diagram);
		final String[] pp = path.split("/");
		StringBuilder pF = new StringBuilder();
		for (int i = 0; i < pp.length - 1; i++) { pF.append(pp[i]).append("/"); }
		return pF.toString();
	}

	/**
	 * Checks for syntax error.
	 *
	 * @param fp
	 *            the fp
	 * @param expression
	 *            the expression
	 * @param isExpression
	 *            the is expression
	 * @return true, if successful
	 */
	public static boolean hasSyntaxError(final IFeatureProvider fp, final String expression,
			final boolean isExpression) {
		return hasSyntaxError(fp, expression, isExpression, false);
	}

	/**
	 * Checks for syntax error.
	 *
	 * @param fp
	 *            the fp
	 * @param expression
	 *            the expression
	 * @param isExpression
	 *            the is expression
	 * @param isString
	 *            the is string
	 * @return true, if successful
	 */
	public static boolean hasSyntaxError(final IFeatureProvider fp, final String expression, final boolean isExpression,
			final boolean isString) {
		if (expression.replace(" ", "").isEmpty()) return false;
		URI du = ((GamaDiagramEditor) ExampleUtil.getDiagramEditor(fp)).getDiagramEditorInput().getUri();

		final URI uri = syntaxCheckURI(du);

		final XtextResourceSet rs = new SynchronizedXtextResourceSet();
		rs.setClasspathURIContext(ModelGenerator.class);
		final String gamlModel = "model toto2733663525\nglobal{init{" + (isExpression ? "string _toto_t <-" : "")
				+ (isString ? "\"" : "") + expression + (isString ? "\"" : "") + (isExpression ? ";" : "") + "}}";
		final InputStream is = new ByteArrayInputStream(gamlModel.getBytes());
		final GamlResource resource = (GamlResource) rs.createResource(uri);

		try {
			resource.load(is, null);
		} catch (final IOException e1) {
			e1.printStackTrace();
		}
		try {
			resource.validate();
			for (GamlCompilationError error : resource.getValidationContext().getInternalErrors()) {
				if (error.isError() && "Syntax errors detected ".equals(error.toString())) return true;
			}
			return false;
		} catch (final Exception e) {
			return true;
		}
	}

	static URI syntaxCheckURI(final URI diagramURI) {
		// Compiler listeners and validation contexts are keyed by URI, even across resource sets.
		return diagramURI.trimFragment().trimFileExtension().appendFileExtension("syntax-check.gaml");
	}

	/**
	 * Model validation.
	 *
	 * @param fp
	 *            the fp
	 * @param diagram
	 *            the diagram
	 * @return the list
	 */
	public static List<GamlCompilationError> modelValidation(final IFeatureProvider fp, final Diagram diagram) {

		final GamaDiagramEditor diagramEditor = (GamaDiagramEditor) ExampleUtil.getDiagramEditor(fp);
		diagramEditor.initIdsEObjects();

		final XtextResourceSet rs = new SynchronizedXtextResourceSet();
		rs.setClasspathURIContext(ModelGenerator.class);
		URI du = diagramEditor.getDiagramEditorInput().getUri();
		final URI uri = URI.createURI(du.trimFragment().toString().replace(".gadl", ".gaml"), true);
		final GamlResource resource = (GamlResource) rs.createResource(uri);
		final String gamlModel = ModelGenerator.generateModel(fp, diagram, null);
		if ("".equals(gamlModel)) return null;
		final InputStream is = new ByteArrayInputStream(gamlModel.getBytes());
		diagramEditor.setResource(resource);
		try {
			resource.load(is, null);
		} catch (final IOException e1) {
			e1.printStackTrace();
		}
		try {

			resource.getValidationContext().clear();
			final List<GamlCompilationError> errors = validateDiagramResource(resource, diagramEditor);
			diagramEditor.getErrorsLoc().clear();

			diagramEditor.setErrors(errors);
			if (errors.isEmpty()) { diagramEditor.getSyntaxErrorsLoc().clear(); }
			return errors;
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	static List<GamlCompilationError> validateDiagramResource(final GamlResource resource,
			final IGamlBuilderListener listener) {
		final IModelDescription description = resource.buildCompleteDescription();
		try {
			final IModelDescription validated = description == null ? null : description.validate();
			final List<IExperimentDescription> experiments = new ArrayList<>();
			if (validated != null) {
				for (final IExperimentDescription experiment : validated.getExperiments()) {
					if (!experiment.isAbstract()) experiments.add(experiment);
				}
			}
			final List<GamlCompilationError> errors = new ArrayList<>();
			for (final GamlCompilationError error : resource.getValidationContext().getInternalErrors()) {
				if (error.isError()) errors.add(error);
			}
			// Publish before disposal, directly to this editor rather than the URI-keyed global listener.
			listener.validationEnded(validated, experiments, resource.getValidationContext());
			return errors;
		} finally {
			if (description != null) description.dispose();
		}
	}

	/**
	 * Manage name.
	 *
	 * @param name
	 *            the name
	 * @return the string
	 */
	static String manageName(final String name) {
		if (name.contains(" ")) return "\"" + name + "\"";
		return name;
	}

	/**
	 * Define species.
	 *
	 * @param species
	 *            the species
	 * @param level
	 *            the level
	 * @param id
	 *            the id
	 * @return the string
	 */
	static String defineSpecies(final ESpecies species, final int level, final String id) {
		if (species == null) return "";
		StringBuilder model = new StringBuilder().append(EL);
		String sp = "";
		for (int i = 0; i < level; i++) { sp += "\t"; }
		model.append(sp);
		if (species instanceof EClassSpecies) {
			model.append("class ").append(manageName(species.getName()));
		} else if (species instanceof EGrid) {
			model.append("grid ").append(manageName(species.getName()));
		} else {
			model.append("species ").append(manageName(species.getName()));
		}

		if (species.getInheritsFrom() != null) {
			model.append(" parent:").append(manageName(species.getInheritsFrom().getName()));
		}

		if (species.getSkills() != null && !species.getSkills().isEmpty()) {
			model.append(" skills:").append(species.getSkills());
		}
		for (final EFacet facet : species.getFacets()) {
			if (facet.getValue().replace(" ", "").isEmpty() || "name".equals(facet.getName())
					|| "skills".equals(facet.getName())) {
				continue;
			}
			model.append(" ").append(facet.getName()).append(":").append(facet.getValue());
		}
		model.append(" {").append(EL);
		model.append(defineSpeciesBody(species, level, id));
		model.append(sp).append("}").append(EL);
		return model.toString();
	}

	public static List<EReflexLink> orderedReflexes(final ESpecies species) {
		final var remaining = new ArrayList<>(species.getReflexLinks());
		remaining.removeIf(link -> link.getReflex() == null);
		final List<EReflexLink> result = new ArrayList<>();
		for (final var link : List.copyOf(remaining)) {
			if (!"reflex".equals(link.getReflex().getKind())) {
				result.add(link);
				remaining.remove(link);
			}
		}
		for (final String name : species.getReflexList()) {
			final var next = remaining.stream().filter(link -> java.util.Objects.equals(name == null ? "" : name,
					link.getReflex().getName() == null ? "" : link.getReflex().getName()))
					.findFirst().orElse(null);
			if (next != null) {
				result.add(next);
				remaining.remove(next);
			}
		}
		result.addAll(remaining);
		return result;
	}

	private static String defineSpeciesBody(final ESpecies species, final int level, final String id) {
		final StringBuilder model = new StringBuilder();
		for (final EVariable var : species.getVariables()) { model.append(defineVariable(var, level + 1)); }
		String idT = ModelStructure.getElementId(species);
		boolean isId = idT.equals(id);
		if (isId) { model.append(EL).append(idT).append(EL); }
		if (!(species instanceof EClassSpecies)
				&& (!(species instanceof EExperiment) || species.getInit() != null && !species.getInit().isBlank())) {
			model.append(defineInit(species, isId ? -1 : level + 1));
		}
		if (isId) { model.append(EL).append(idT).append(EL); }

		for (final EActionLink link : species.getActionLinks()) {
			final String idA = ModelStructure.getElementId(link.getAction());
			isId = idA.equals(id);
			if (isId) { model.append(EL).append(idA).append(EL); }
			model.append(defineAction(link, isId ? -1 : level + 1));
			if (isId) { model.append(EL).append(idA).append(EL); }
		}
		for (final EReflexLink reflex : orderedReflexes(species)) {
			idT = ModelStructure.getElementId(reflex.getReflex());
			isId = idT.equals(id);
			if (isId) { model.append(EL).append(idT).append(EL); }
			model.append(defineGeneric(reflex, isId ? -1 : level + 1));
			if (isId) { model.append(EL).append(idT).append(EL); }
		}

		for (final EStateLink link : species.getStateLinks()) {
			final String idT2 = ModelStructure.getElementId(link.getState());
			final boolean isId2 = idT2.equals(id);
			if (isId2) { model.append(EL).append(idT2).append(EL); }
			model.append(defineGeneric(link, isId2 ? -1 : level));
			if (isId2) { model.append(EL).append(idT2).append(EL); }
		}
		for (final ETaskLink link : species.getTaskLinks()) {
			final String idT2 = ModelStructure.getElementId(link.getTask());
			final boolean isId2 = idT2.equals(id);
			if (isId2) { model.append(EL).append(idT2).append(EL); }
			model.append(defineGeneric(link, isId2 ? -1 : level));
			if (isId2) { model.append(EL).append(idT2).append(EL); }
		}
		for (final EEquationLink link : species.getEquationLinks()) {
			final String idT2 = ModelStructure.getElementId(link.getEquation());
			final boolean isId2 = idT2.equals(id);
			if (isId2) { model.append(EL).append(idT2).append(EL); }
			model.append(defineGeneric(link, isId2 ? -1 : level));
			if (isId2) { model.append(EL).append(idT2).append(EL); }
		}
		for (final EPlanLink link : species.getPlanLinks()) {
			final String idT2 = ModelStructure.getElementId(link.getPlan());
			final boolean isId2 = idT2.equals(id);
			if (isId2) { model.append(EL).append(idT2).append(EL); }
			model.append(defineGeneric(link, isId2 ? -1 : level));
			if (isId2) { model.append(EL).append(idT2).append(EL); }
		}
		for (final ERuleLink link : species.getRuleLinks()) {
			final String idT2 = ModelStructure.getElementId(link.getRule());
			final boolean isId2 = idT2.equals(id);
			if (isId2) { model.append(EL).append(idT2).append(EL); }
			model.append(defineGeneric(link, isId2 ? -1 : level));
			if (isId2) { model.append(EL).append(idT2).append(EL); }
		}
		for (final EPerceiveLink link : species.getPerceiveLinks()) {
			final String idT2 = ModelStructure.getElementId(link.getPerceive());
			final boolean isId2 = idT2.equals(id);
			if (isId2) { model.append(EL).append(idT2).append(EL); }
			model.append(defineGeneric(link, isId2 ? -1 : level));
			if (isId2) { model.append(EL).append(idT2).append(EL); }
		}

		for (final EAspectLink link : species.getAspectLinks()) {
			final String idA = ModelStructure.getElementId(link.getAspect());
			isId = idA.equals(id);
			if (isId) { model.append(EL).append(idA).append(EL); }
			model.append(defineAspect(link, isId ? -1 : level + 1));
			if (isId) { model.append(EL).append(idA).append(EL); }
		}
		for (final ESubSpeciesLink link : species.getMicroSpeciesLinks()) {
			model.append(defineSpecies(link.getMicro(), level + 1, id));
		}

		return model.toString();
	}

	/**
	 * Define variable.
	 *
	 * @param var
	 *            the var
	 * @param level
	 *            the level
	 * @return the string
	 */
	static String defineVariable(final EVariable var, final int level) {
		if (var == null) return "";
		StringBuilder varStr = new StringBuilder();
		for (int i = 0; i < level; i++) { varStr.append("\t"); }
		if (var.getType() == null || "".equals(var.getType())) {
			varStr.append("var ");
		} else {
			varStr.append(var.getType()).append(" ");
		}
		varStr.append(var.getName());
		if (var.getInit() != null && !"".equals(var.getInit())) { varStr.append(" <- ").append(var.getInit()); }
		if (var.getUpdate() != null && !"".equals(var.getUpdate())) {
			varStr.append(" update: ").append(var.getUpdate());
		}
		if (var.getFunction() != null && !"".equals(var.getFunction())) {
			varStr.append(" function: ").append(var.getFunction());
		}
		if (var.getMin() != null && !"".equals(var.getMin())) { varStr.append(" min: ").append(var.getMin()); }
		if (var.getMax() != null && !"".equals(var.getMax())) { varStr.append(" max: ").append(var.getMax()); }
		varStr.append(";").append(EL);
		return varStr.toString();
	}

	/**
	 * Define action.
	 *
	 * @param link
	 *            the link
	 * @param level
	 *            the level
	 * @return the string
	 */
	static String defineAction(final EActionLink link, final int level) {
		if (link == null || link.getAction() == null) return "";

		final EAction action = link.getAction();
		StringBuilder result = new StringBuilder();
		String sp = "";
		for (int i = 0; i < level; i++) { sp += "\t"; }

		final String returnType =
				action.getReturnType() == null || action.getReturnType().isEmpty() ? "action" : action.getReturnType();
		String arguments = "";
		for (final EVariable var : action.getVariables()) {
			arguments += (arguments.isEmpty() ? "" : ", ") + var.getType() + " " + var.getName()
					+ (var.getInit() == null || var.getInit().isEmpty() ? "" : " <- " + var.getInit());
		}
		result.append(sp).append(returnType).append(" ").append(manageName(action.getName()))
				.append("(").append(arguments).append(")");
		boolean virtual = false;
		for (final EFacet facet : action.getFacets()) {
			if (facet.getValue() == null || facet.getValue().isBlank()
					|| List.of("name", "type", "returns").contains(facet.getName())) continue;
			result.append(" ").append(facet.getName()).append(":").append(facet.getValue());
			if ("virtual".equals(facet.getName()) && "true".equals(facet.getValue().trim())) virtual = true;
		}
		if (virtual) return result.append(";").append(EL).toString();
		result.append(" {").append(EL);
		final String code = action.getGamlCode();
		if (code != null && !code.isEmpty()) { result.append(code); }
		result.append(EL).append(sp).append("}").append(EL);
		return result.toString();
	}

	/**
	 * Define init.
	 *
	 * @param species
	 *            the species
	 * @param level
	 *            the level
	 * @return the string
	 */
	static String defineInit(final ESpecies species, final int level) {
		if (species == null) return "";
		StringBuilder result = new StringBuilder();
		final String code = species.getInit();
		if ((code == null || code.isBlank()) && species.getReflexLinks().stream()
				.anyMatch(link -> link.getReflex() != null && "init".equals(link.getReflex().getKind()))) return "";
		String sp = "";
		for (int i = 0; i < level; i++) { sp += "\t"; }
		result.append(sp).append("init {").append(EL);
		if (code != null && !code.isEmpty()) { result.append(code); }
		result.append(EL).append(sp).append("}").append(EL);
		return result.toString();
	}

	/**
	 * Define generic.
	 *
	 * @param link
	 *            the link
	 * @param level
	 *            the level
	 * @return the string
	 */
	static String defineGeneric(final EGamaLink link, final int level) {
		if (link == null || link.getTarget() == null) return "";
		final EGamaObject object = link.getTarget();
		StringBuilder result = new StringBuilder();
		StringBuilder sp = new StringBuilder();
		for (int i = 0; i < level; i++) { sp.append("\t"); }
		String type = "";
		if (object instanceof EReflex reflex) {
			type = reflex.getKind();
		} else if (object instanceof EState) {
			type = "state";
		} else if (object instanceof ETask) {
			type = "task";
		} else if (object instanceof EEquation) {
			type = "equation";
		} else if (object instanceof EPlan) {
			type = "plan";
		} else if (object instanceof EPerceive) {
			type = "perceive";
		} else if (object instanceof ERule) { type = "rule"; }
		result.append(type);
		if (object.getName() != null && !object.getName().isBlank()) {
			result.append(" ").append(manageName(object.getName()));
		}
		for (final EFacet facet : object.getFacets()) {
			if (facet.getValue().replace(" ", "").isEmpty()) { continue; }
			result.append(" ").append(facet.getName()).append(":").append(facet.getValue());
		}
		if (object instanceof ERule) return result.append(";").append(EL).toString();
		String code = null;
		if (object instanceof EReflex) {
			code = ((EReflex) object).getGamlCode();
		} else if (object instanceof EState) {
			code = ((EState) object).getGamlCode();
		} else if (object instanceof EEquation) {
			code = ((EEquation) object).getGamlCode();
		} else if (object instanceof ETask) {
			code = ((ETask) object).getGamlCode();
		} else if (object instanceof EPlan) {
			code = ((EPlan) object).getGamlCode();
		} else if (object instanceof EPerceive) {
			code = ((EPerceive) object).getGamlCode();
		}
		result.append(" {").append(EL);
		if (code != null && !code.isEmpty()) { result.append(code); }

		result.append(EL).append(sp.toString()).append("}").append(EL);
		return result.toString();
	}

	/**
	 * Define aspect.
	 *
	 * @param link
	 *            the link
	 * @param level
	 *            the level
	 * @return the string
	 */
	static String defineAspect(final EAspectLink link, final int level) {
		if (link == null || link.getAspect() == null) return "";
		StringBuilder result = new StringBuilder();
		final EAspect asp = link.getAspect();
		String sp = "";
		for (int i = 0; i < level; i++) { sp += "\t"; }
		result.append(sp).append("aspect ").append(manageName(asp.getName())).append(" {").append(EL);
		if (asp.isDefineGamlCode()) {
			final String code = asp.getGamlCode();
			if (code != null && !code.isEmpty()) {
				/*
				 * for (final String line : code.split("\n")) { if (line.replace(" ", "").replace("\t", "").isEmpty())
				 * continue; result += sp + (level == -1 ? "" : "\t") + line; }
				 */
				result.append(code);
			}
		} else {

			for (final ELayerAspect lay : asp.getLayers()) {
				StringBuilder code = new StringBuilder().append(sp).append(level >= 0 ? "\t" : "").append("draw ");
				final String val = lay.getShapeType();
				if (val == null) { continue; }
				if ("polyline".equals(val) || "polygon".equals(val)) {
					code.append(val).append("(").append(lay.getPoints()).append(")");
				} else if ("circle".equals(val) || "sphere".equals(val)) {
					code.append(val).append("(").append(lay.getRadius()).append(")");
				} else if ("square".equals(val) || "cube".equals(val) || "pyramid".equals(val)) {
					code.append(val).append("(").append(lay.getSize()).append(")");
				} else if ("rectangle".equals(val) || "hexagon".equals(val)) {
					code.append(val).append("({").append(lay.getWidth()).append(",").append(lay.getHeigth())
							.append("})");
				} else if ("expression".equals(val)) {
					code.append(lay.getExpression());
				} else if ("image".equals(val)) {
					code.append("file(\"").append(lay.getPath()).append("\")")
							.append(lay.getImageSize() != null && !lay.getImageSize().isEmpty() ? ""
									: " size:" + lay.getSize());
				} else if ("text".equals(val)) {
					code.append("\"").append(lay.getText()).append("\"").append(
							lay.getTextSize() != null && !lay.getTextSize().isEmpty() ? "" : " size:" + lay.getSize());
				}
				if (lay.getIsColorCst()) {
					code.append(" color: rgb(").append(lay.getColorRBG().toString().replace("[", "").replace("]", ""))
							.append(")");
				} else if (lay.getColor() != null && !lay.getColor().isEmpty()) {
					code.append(" color: ").append(lay.getColor());
				}
				if (lay.getEmpty() != null && !lay.getEmpty().isEmpty() && !"false".equals(lay.getEmpty())) {
					code.append(" empty: ").append(lay.getEmpty());
				}
				if (lay.getRotate() != null && !lay.getRotate().isEmpty() && !"0.0".equals(lay.getRotate())) {
					code.append(" rotate: ").append(lay.getRotate());
				}
				if (lay.getAt() != null && !lay.getAt().isEmpty()) { code.append(" at: ").append(lay.getAt()); }
				if (lay.getDepth() != null && !lay.getDepth().isEmpty() && !"0.0".equals(lay.getDepth())) {
					code.append(" depth: ").append(lay.getDepth());
				}
				if (lay.getTexture() != null && !lay.getTexture().isEmpty() && !"[]".equals(lay.getTexture())) {
					code.append(" texture: ").append(lay.getTexture());
				}
				result.append(code.toString()).append(";").append(EL);
			}
		}
		result.append(EL).append(sp).append("}").append(EL);
		return result.toString();
	}

	/**
	 * Define experiment.
	 *
	 * @param exp
	 *            the exp
	 * @param id
	 *            the id
	 * @return the string
	 */
	static String defineExperiment(final EExperiment exp, final String id) {
		if (exp == null) return "";
		final String kind = ExperimentKind.of(exp);
		String model = EL + EL + "experiment " + gama.api.utils.StringUtils.toGamlString(exp.getName())
				+ " type:" + kind + " ";
		for (final EFacet facet : exp.getFacets()) {
			if ("type".equals(facet.getName()) || facet.getValue() == null || facet.getValue().isBlank()) continue;
			model += " " + facet.getName() + ":" + facet.getValue();
		}
		if (!exp.getSkills().isEmpty() && exp.getFacets().stream().noneMatch(f -> "skills".equals(f.getName()))) {
			model += " skills:" + exp.getSkills();
		}
		if (exp.getInheritsFrom() != null && exp.getFacets().stream().noneMatch(f -> "parent".equals(f.getName()))) {
			model += " parent:" + exp.getInheritsFrom().getName();
		}
		model += " {" + EL + defineSpeciesBody(exp, 0, id);
		if (exp.getGamlCode() != null) model += exp.getGamlCode();
		for (final EParameter parameter : exp.getParameters()) model += defineParameter(parameter);
		if ("gui".equals(kind) || !exp.getMonitors().isEmpty() || !exp.getDisplayLinks().isEmpty()) {
			model += "\toutput{";
			for (final EMonitor mon : exp.getMonitors()) { model += defineMonitor(mon); }
			for (final EDisplayLink link : exp.getDisplayLinks()) {
				final String idA = ModelStructure.getElementId(link.getDisplay());
				final boolean isId = idA.equals(id);
				if (isId) { model += EL + idA + EL; }
				model += defineDisplay(link);
				if (isId) { model += EL + idA + EL; }

			}
			model += EL + "\t}" + EL;
		}
		return model + "}" + EL;
	}

	/**
	 * Define parameter.
	 *
	 * @param par
	 *            the par
	 * @return the string
	 */
	static String defineParameter(final EParameter par) {
		if (par == null) return "";
		StringBuilder parStr = new StringBuilder("\tparameter ");
		parStr.append(gama.api.utils.StringUtils.toGamlString(par.getName()));
		parStr.append(" var:").append(par.getVariable());
		for (final var facet : parameterFacets(par).entrySet()) {
			parStr.append(" ").append("init".equals(facet.getKey()) ? "<-" : facet.getKey() + ":")
					.append(facet.getValue());
		}
		parStr.append(";").append(EL);
		return parStr.toString();
	}

	public static java.util.Map<String, String> parameterFacets(final EParameter parameter) {
		final var facets = new java.util.LinkedHashMap<String, String>();
		facets.put("init", parameter.getInit());
		facets.put("min", parameter.getMin());
		facets.put("max", parameter.getMax());
		facets.put("step", parameter.getStep());
		facets.put("among", parameter.getAmong());
		if (parameter.getCategory() != null && !parameter.getCategory().isBlank()) {
			facets.put("category", gama.api.utils.StringUtils.toGamlString(parameter.getCategory()));
		}
		for (final var facet : parameter.getFacets()) {
			if (!List.of("name", "var").contains(facet.getName())) facets.put(facet.getName(), facet.getValue());
		}
		facets.values().removeIf(value -> value == null || value.isBlank());
		return facets;
	}

	/**
	 * Define monitor.
	 *
	 * @param mon
	 *            the mon
	 * @return the string
	 */
	static String defineMonitor(final EMonitor mon) {
		if (mon == null) return "";
		StringBuilder parStr = new StringBuilder("\t\tmonitor ");
		parStr.append(gama.api.utils.StringUtils.toGamlString(mon.getName()));
		if (mon.getValue() != null && !mon.getValue().isEmpty()) { parStr.append(" value:").append(mon.getValue()); }
		for (final var facet : mon.getFacets()) {
			if (List.of("name", "value").contains(facet.getName()) || facet.getValue() == null || facet.getValue().isBlank()) continue;
			parStr.append(" ").append(facet.getName()).append(":").append(facet.getValue());
		}
		parStr.append(";").append(EL);
		return parStr.toString();
	}

	/**
	 * Define display.
	 *
	 * @param link
	 *            the link
	 * @return the string
	 */
	static String defineDisplay(final EDisplayLink link) {
		if (link == null || link.getDisplay() == null) return "";
		final EDisplay disp = link.getDisplay();
		StringBuilder model = new StringBuilder().append(EL).append("\t\t");
		model.append("display ").append(gaml.compiler.ui.wizards.experiment.ExperimentDraft.quoteName(disp.getName()));
		for (final EFacet facet : disp.getFacets()) {
			if (facet.getValue() == null || facet.getValue().isBlank()) { continue; }
			model.append(" ").append(facet.getName()).append(":").append(facet.getValue());

		}

		model.append(" {").append(EL);
		model.append(displayBody(disp));
		model.append("\t\t}");
		return model.toString();
	}

	public static String displayBody(final EDisplay disp) {
		final var model = new StringBuilder();
		if (disp.isDefineGamlCode()) {
			final String code = disp.getGamlCode();
			if (code != null) model.append(code);
		} else {
			for (final ELayer lay : disp.getLayers()) {
				model.append(defineLayer(lay));
			}
			if (disp.getTrailingSource() != null) model.append(disp.getTrailingSource());
		}
		return model.toString();
	}

	public static String defineLayer(final ELayer lay) {
		if (lay.getGamlCode() != null && !lay.getGamlCode().isBlank()) return lay.getGamlCode();
		final var model = new StringBuilder();
		if (lay.getType() != null) {
				model.append("\n\t\t\t");
				if ("species".equals(lay.getType())) {
					model.append(lay.getType()).append(" ").append(lay.getSpecies()).append(
							lay.getAspect() == null || lay.getAspect().isEmpty() || "default".equals(lay.getAspect())
									? "" : " aspect: " + lay.getAspect());
				} else if ("grid".equals(lay.getType())) {
					model.append(lay.getType()).append(" ").append(lay.getGrid());
					if (lay.getIsColorCst() == null || lay.getIsColorCst()) {
						if (lay.getColorRBG().size() == 3 && lay.isShowLines()) {
							model.append(" border:rgb(").append(lay.getColorRBG().get(0)).append(",")
									.append(lay.getColorRBG().get(1)).append(",").append(lay.getColorRBG().get(2))
									.append(")");
						}
					} else if (lay.getColor() != null && !"rgb(255,255,255)".equals(lay.getColor())
							&& !lay.getColor().isEmpty()) {
						model.append(" border:").append(lay.getColor());
					}
				} else if ("agents".equals(lay.getType())) {
					model.append(lay.getType()).append(" ").append(gama.api.utils.StringUtils.toGamlString(lay.getName())).append(" value:")
							.append(lay.getAgents()).append(lay.getAspect() == null || lay.getAspect().isEmpty() ? ""
									: " aspect: " + lay.getAspect());
				} else if ("image".equals(lay.getType())) {
					model.append(lay.getType()).append(" ").append(gama.api.utils.StringUtils.toGamlString(lay.getFile()))
							.append(lay.getSize() == null || lay.getSize().isBlank() ? "" : " size: " + lay.getSize());
					if (lay.getIsColorCst() == null || lay.getIsColorCst()) {
						if (lay.getColorRBG().size() == 3
								&& (!"255".equals(lay.getColorRBG().get(0)) || !"255".equals(lay.getColorRBG().get(1))
										|| !"255".equals(lay.getColorRBG().get(2)))) {
							model.append(" color:rgb(").append(lay.getColorRBG().get(0)).append(",")
									.append(lay.getColorRBG().get(1)).append(",").append(lay.getColorRBG().get(2))
									.append(")");
						}
					} else if (lay.getColor() != null && !"rgb(255,255,255)".equals(lay.getColor())
							&& !lay.getColor().isEmpty()) {
						model.append(" color:").append(lay.getColor());
					}
				} else if ("text".equals(lay.getType())) {
					model.append("graphics ").append(gama.api.utils.StringUtils.toGamlString(lay.getName()))
							.append(" { draw ").append(gama.api.utils.StringUtils.toGamlString(lay.getText()))
							.append(lay.getSize() == null || lay.getSize().isBlank() ? "" : " size: " + lay.getSize());
					if (lay.getIsColorCst() == null || lay.getIsColorCst()) {
						if (lay.getColorRBG().size() == 3
								&& (!"255".equals(lay.getColorRBG().get(0)) || !"255".equals(lay.getColorRBG().get(1))
										|| !"255".equals(lay.getColorRBG().get(2)))) {
							model.append(" color:rgb(").append(lay.getColorRBG().get(0)).append(",")
									.append(lay.getColorRBG().get(1)).append(",").append(lay.getColorRBG().get(2))
									.append(")");
						}
					} else if (lay.getColor() != null && !"rgb(255,255,255)".equals(lay.getColor())
							&& !lay.getColor().isEmpty()) {
						model.append(" color:").append(lay.getColor());
					}
				} else if ("chart".equals(lay.getType())) {
					String background = "";
					if (lay.getIsColorCst() == null || lay.getIsColorCst()) {
						if (lay.getColorRBG().size() == 3
								&& (!"255".equals(lay.getColorRBG().get(0)) || !"255".equals(lay.getColorRBG().get(1))
										|| !"255".equals(lay.getColorRBG().get(2)))) {
							background = " background:rgb(" + lay.getColorRBG().get(0) + "," + lay.getColorRBG().get(1)
									+ "," + lay.getColorRBG().get(2) + ")";
						}
					} else if (lay.getColor() != null && !"rgb(255,255,255)".equals(lay.getColor())
							&& !lay.getColor().isEmpty()) {
						background = " background:" + lay.getColor();
					}
					model.append(lay.getType()).append(" ").append(gama.api.utils.StringUtils.toGamlString(lay.getName())).append(" type:")
							.append(lay.getChart_type()).append(background);
				}

				for (final EFacet facet : lay.getFacets()) {
					if (facet.getValue() == null || facet.getValue().isBlank()) { continue; }
					model.append(" ").append(facet.getName()).append(":").append(facet.getValue());
				}
				if ("chart".equals(lay.getType())) {
					model.append("{").append(EL);
					if (lay.getChartlayers() != null && !lay.getChartlayers().isEmpty()) {
						for (final EChartLayer cl : lay.getChartlayers()) {
							model.append("\t\t\t\tdata ").append(gama.api.utils.StringUtils.toGamlString(cl.getName())).append(" style:")
									.append(cl.getStyle()).append(" value:")
									.append(cl.getValue() == null || cl.getValue().isEmpty() ? "0.0" : cl.getValue())
									.append(cl.getColor() == null || cl.getColor().isEmpty() ? ""
											: " color:" + cl.getColor())
									.append(";").append(EL);
						}
					}
					model.append(EL).append("\t\t\t}").append(EL);
				} else {
					model.append(";").append("text".equals(lay.getType()) ? " }" : "").append(EL);
				}
		}
		return model.toString();
	}

	/**
	 * Generate model.
	 *
	 * @param fp
	 *            the fp
	 * @param diagram
	 *            the diagram
	 * @param id
	 *            the id
	 * @return the string
	 */
	public static String generateModel(final IFeatureProvider fp, final Diagram diagram, final String id) {
		String model = "";
		final List<Shape> contents = diagram.getChildren();
		if (contents != null) {
			EWorldAgent worldAgent = null;
			for (final Shape obj : contents) {
				final Object bo = fp.getBusinessObjectForPictogramElement(obj);
				if (bo instanceof EWorldAgent) {
					worldAgent = (EWorldAgent) bo;
					break;

				}
			}
			if (worldAgent == null) return "";
			String modelName = diagram.getName();
			if (!Character.isLetter(modelName.charAt(0))) { modelName = "_" + modelName; }
			final var documentation = DiagramModelDocumentation.read(diagram);
			model = (documentation.isEmpty() ? "" : documentation.comment(modelName, EL) + EL)
					+ "model " + modelName + EL + EL + "global";

			if (worldAgent.getSkills() != null && !worldAgent.getSkills().isEmpty()) {
				model += " skills:" + worldAgent.getSkills();
			}
			for (final EFacet facet : worldAgent.getFacets()) {
				if (facet.getValue().replace(" ", "").isEmpty()) { continue; }
				model += " " + facet.getName() + ":" + facet.getValue();
			}

			model += " {" + EL;
			final int level = 1;
			for (final EVariable var : worldAgent.getVariables()) { model += defineVariable(var, level); }
			final String worldId = ModelStructure.getElementId(worldAgent);
			final boolean isWorldId = worldId.equals(id);
			if (isWorldId) { model += EL + worldId + EL; }
			model += defineInit(worldAgent, isWorldId ? -1 : 1);
			if (isWorldId) { model += EL + worldId + EL; }

			for (final EActionLink link : worldAgent.getActionLinks()) {
				final String idA = ModelStructure.getElementId(link.getAction());
				final boolean isId = idA.equals(id);
				if (isId) { model += EL + idA + EL; }
				model += defineAction(link, isId ? -1 : level);
				if (isId) { model += EL + idA + EL; }
			}
			for (final EReflexLink reflex : orderedReflexes(worldAgent)) {
				final String idT = ModelStructure.getElementId(reflex.getReflex());
				final boolean isId = idT.equals(id);
				if (isId) { model += EL + idT + EL; }
				model += defineGeneric(reflex, isId ? -1 : level);
				if (isId) { model += EL + idT + EL; }
			}

			for (final EStateLink link : worldAgent.getStateLinks()) {
				final String idT = ModelStructure.getElementId(link.getState());
				final boolean isId = idT.equals(id);
				if (isId) { model += EL + idT + EL; }
				model += defineGeneric(link, isId ? -1 : level);
				if (isId) { model += EL + idT + EL; }

			}
			for (final ETaskLink link : worldAgent.getTaskLinks()) {
				final String idT = ModelStructure.getElementId(link.getTask());
				final boolean isId = idT.equals(id);
				if (isId) { model += EL + idT + EL; }
				model += defineGeneric(link, isId ? -1 : level);
				if (isId) { model += EL + idT + EL; }
			}
			for (final EEquationLink link : worldAgent.getEquationLinks()) {
				final String idT = ModelStructure.getElementId(link.getEquation());
				final boolean isId = idT.equals(id);
				if (isId) { model += EL + idT + EL; }
				model += defineGeneric(link, isId ? -1 : level);
				if (isId) { model += EL + idT + EL; }
			}
			for (final EPlanLink link : worldAgent.getPlanLinks()) {
				final String idT = ModelStructure.getElementId(link.getPlan());
				final boolean isId = idT.equals(id);
				if (isId) { model += EL + idT + EL; }
				model += defineGeneric(link, isId ? -1 : level);
				if (isId) { model += EL + idT + EL; }
			}
			for (final ERuleLink link : worldAgent.getRuleLinks()) {
				final String idT = ModelStructure.getElementId(link.getRule());
				final boolean isId = idT.equals(id);
				if (isId) { model += EL + idT + EL; }
				model += defineGeneric(link, isId ? -1 : level);
				if (isId) { model += EL + idT + EL; }
			}
			for (final EPerceiveLink link : worldAgent.getPerceiveLinks()) {
				final String idT = ModelStructure.getElementId(link.getPerceive());
				final boolean isId = idT.equals(id);
				if (isId) { model += EL + idT + EL; }
				model += defineGeneric(link, isId ? -1 : level);
				if (isId) { model += EL + idT + EL; }
			}

			for (final EAspectLink link : worldAgent.getAspectLinks()) {

				final String idT = ModelStructure.getElementId(link.getAspect());
				final boolean isId = idT.equals(id);
				if (isId) { model += EL + idT + EL; }
				model += defineAspect(link, isId ? -1 : level);
				if (isId) { model += EL + idT + EL; }
			}
			model += "}";
			model += EL;
			for (final ESubSpeciesLink link : worldAgent.getMicroSpeciesLinks()) {
				model += defineSpecies(link.getMicro(), 0, id);
			}

			model += EL;

			for (final EExperimentLink link : worldAgent.getExperimentLinks()) {
				model += defineExperiment(link.getExperiment(), id);
			}

		}
		return model;
	}
}
