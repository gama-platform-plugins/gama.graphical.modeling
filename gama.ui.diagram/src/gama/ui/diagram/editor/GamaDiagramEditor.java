/*******************************************************************************************************
 *
 * GamaDiagramEditor.java, in gama.ui.diagram, is part of the source code of the GAMA modeling and
 * simulation platform (v.1.9.3).
 *
 * (c) 2007-2024 UMI 209 UMMISCO IRD/SU & Partners (IRIT, MIAT, TLU, CTU)
 *
 * Visit https://github.com/gama-platform/gama for license information and contacts.
 *
 ********************************************************************************************************/
package gama.ui.diagram.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.graphiti.internal.util.T;
import org.eclipse.graphiti.mm.algorithms.GraphicsAlgorithm;
import org.eclipse.graphiti.mm.algorithms.AbstractText;
import org.eclipse.graphiti.mm.algorithms.MultiText;
import org.eclipse.graphiti.mm.algorithms.RoundedRectangle;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.graphiti.services.IGaService;
import org.eclipse.graphiti.ui.editor.DiagramBehavior;
import org.eclipse.graphiti.ui.editor.DiagramEditor;
import org.eclipse.graphiti.util.IColorConstant;
import org.eclipse.swt.SWT;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import gama.api.kernel.species.IModelSpecies;
import org.eclipse.xtext.resource.SynchronizedXtextResourceSet;
import org.eclipse.xtext.resource.XtextResourceSet;

import gama.api.GAMA;
import gama.api.compilation.GamlCompilationError;
import gama.api.compilation.descriptions.IDescription;
import gama.api.types.map.GamaMapFactory;
import gama.ui.diagram.features.add.ColorDisplay;
import gama.ui.diagram.features.add.RelationStyle;
import gama.ui.diagram.features.layout.LayoutCommonFeature;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.swt.image.GamaImageProvider;
import gama.ui.diagram.metamodel.EAction;
import gama.ui.diagram.metamodel.EAspect;
import gama.ui.diagram.metamodel.EDisplay;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EFacet;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.ELayer;
import gama.ui.diagram.metamodel.ELayerAspect;
import gama.ui.diagram.metamodel.EMonitor;
import gama.ui.diagram.metamodel.EParameter;
import gama.ui.diagram.metamodel.EReflex;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EVariable;
import gama.ui.shared.views.toolbar.GamaToolbar2;
import gama.api.compilation.descriptions.IModelDescription;
import gaml.compiler.ui.editor.GamlEditorState;
import gaml.compiler.ui.editor.toolbar.ExperimentToolbar;
import gaml.compiler.gaml.Model;
import gaml.compiler.gaml.Statement;
import gaml.compiler.gaml.impl.ArgumentDefinitionImpl;
import gaml.compiler.gaml.impl.BlockImpl;
import gaml.compiler.gaml.impl.FacetImpl;
import gaml.compiler.gaml.impl.S_ActionImpl;
import gaml.compiler.gaml.impl.S_DefinitionImpl;
import gaml.compiler.gaml.impl.S_DisplayImpl;
import gaml.compiler.gaml.impl.S_ExperimentImpl;
import gaml.compiler.gaml.impl.S_ReflexImpl;
import gaml.compiler.gaml.impl.S_SpeciesImpl;
import gaml.compiler.gaml.impl.StatementImpl;
import gaml.compiler.gaml.impl.VariableRefImpl;
// speciesOrGridDisplayStatementImpl was removed/renamed in the compiler rework; use StatementImpl instead
import gaml.compiler.resource.GamlResource;
import gaml.compiler.validation.IGamlBuilderListener;
import gama.api.compilation.validation.IValidationContext;

/**
 * The Class GamaDiagramEditor.
 */
public class GamaDiagramEditor extends DiagramEditor implements IGamlBuilderListener {

	/** The resource. */
	GamlResource resource;

	private boolean wasOK = true;
	private ExperimentToolbar experimentToolbar;
	private GamaToolbar2 toolbar;

	/** The diagram. */
	Diagram diagram;

	/** The errors. */
	List<GamlCompilationError> errors;

	/** The facets. */
	List<String> facets = Arrays.asList("torus:", "width:", "height:", "neighbours:", "refresh_every:", "background:",
			"among:", "->", "<-", "step:", "min:", "max:", "update:", "refresh:", "size:", "position:", "background:",
			"transparency:", "color:", "empty:", "rotate:", "schedules:", "at:", "depth:", "texture:");

	/** The to refresh. */
	boolean toRefresh = true;

	/** The ids E objects. */
	Map<List<String>, EObject> idsEObjects = GamaMapFactory.create();

	/** The errors loc. */
	Map<List<String>, Map<String, String>> errorsLoc = GamaMapFactory.create();

	/** The syntax errors loc. */
	Map<List<String>, Map<String, String>> syntaxErrorsLoc = GamaMapFactory.create();

	/**
	 * Instantiates a new gama diagram editor.
	 */
	public GamaDiagramEditor() {}

	private void updateAppearanceIfNeeded() {
		final Diagram currentDiagram = getDiagramTypeProvider().getDiagram();
		if (hasLegacyAppearance(currentDiagram)) {
			getEditingDomain().getCommandStack().execute(new RecordingCommand(getEditingDomain()) {
				@Override
				protected void doExecute() {
					updateDiagramAppearance(currentDiagram);
				}
			});
		}
	}

	private boolean hasLegacyAppearance(final Diagram currentDiagram) {
		if (currentDiagram.getGridUnit() != 0 || currentDiagram.getVerticalGridUnit() != 0
				|| currentDiagram.isSnapToGrid()) return true;
		for (final Shape shape : currentDiagram.getChildren()) {
			final Object businessObject =
					getDiagramTypeProvider().getFeatureProvider().getBusinessObjectForPictogramElement(shape);
			if (!(businessObject instanceof EGamaObject)) continue;
			final EGamaObject gamaObject = (EGamaObject) businessObject;
			final GraphicsAlgorithm graphicsAlgorithm = shape.getGraphicsAlgorithm();
			final List<Integer> color = ColorDisplay.updatedNodeColor(gamaObject.getColorPicto());
			final boolean error = Boolean.TRUE.equals(gamaObject.getHasError());
			if (graphicsAlgorithm == null || !(graphicsAlgorithm instanceof RoundedRectangle)
					|| graphicsAlgorithm.getLineWidth() != (error ? 2 : 1)
					|| !matches(graphicsAlgorithm.getForeground(),
							error ? ColorDisplay.CLASS_FOREGROUND_ERROR : ColorDisplay.CLASS_FOREGROUND_DEFAULT)
					|| !matches(graphicsAlgorithm.getBackground(), color)) return true;
			if (gamaObject.getColorPicto().isEmpty()
					|| !gamaObject.getColorPicto().equals(color)) return true;
			if (shape instanceof ContainerShape) {
				for (final Shape child : ((ContainerShape) shape).getChildren()) {
					if (child.getGraphicsAlgorithm() instanceof org.eclipse.graphiti.mm.algorithms.Image) {
						if (!LayoutCommonFeature.hasCurrentIconSize(
								(org.eclipse.graphiti.mm.algorithms.Image) child.getGraphicsAlgorithm())) return true;
						final String expected = GamaImageProvider.nodeImageId(gamaObject);
						if (expected != null && !expected.equals(
								((org.eclipse.graphiti.mm.algorithms.Image) child.getGraphicsAlgorithm()).getId())) return true;
					}
					if (child.getGraphicsAlgorithm() instanceof AbstractText) {
						final AbstractText text = (AbstractText) child.getGraphicsAlgorithm();
						final boolean title = LayoutCommonFeature.isTitle(text);
						final org.eclipse.graphiti.mm.algorithms.styles.Font font = text.getFont();
						if (!matches(text.getForeground(), ColorDisplay.nodeTextColor(color)) || font == null
								|| font.getSize() != (title ? 14 : 12) || font.isBold() != title
								|| !title && !(text instanceof MultiText)) return true;
					}
				}
			}
		}
		for (final Connection connection : currentDiagram.getConnections()) {
			if (!RelationStyle.isCurrent(connection)) return true;
		}
		return false;
	}

	private boolean matches(final org.eclipse.graphiti.mm.algorithms.styles.Color actual,
			final IColorConstant expected) {
		return actual != null && actual.getRed() == expected.getRed() && actual.getGreen() == expected.getGreen()
				&& actual.getBlue() == expected.getBlue();
	}

	private boolean matches(final org.eclipse.graphiti.mm.algorithms.styles.Color actual,
			final List<Integer> expected) {
		return actual != null && actual.getRed() == expected.get(0) && actual.getGreen() == expected.get(1)
				&& actual.getBlue() == expected.get(2);
	}

	private IColorConstant nodeOutlineColor(final boolean error) {
		return error ? ColorDisplay.CLASS_FOREGROUND_ERROR : ColorDisplay.CLASS_FOREGROUND_DEFAULT;
	}

	private void updateDiagramAppearance(final Diagram currentDiagram) {
		currentDiagram.setGridUnit(0);
		currentDiagram.setVerticalGridUnit(0);
		currentDiagram.setSnapToGrid(false);
		final IGaService gaService = Graphiti.getGaService();
		for (final Shape shape : currentDiagram.getChildren()) {
			final Object businessObject =
					getDiagramTypeProvider().getFeatureProvider().getBusinessObjectForPictogramElement(shape);
			if (!(businessObject instanceof EGamaObject) || shape.getGraphicsAlgorithm() == null) continue;
			final EGamaObject gamaObject = (EGamaObject) businessObject;
			ColorDisplay.updateLegacyNodeColor(gamaObject.getColorPicto());
			final List<Integer> color = gamaObject.getColorPicto();
			final boolean error = Boolean.TRUE.equals(gamaObject.getHasError());
			GraphicsAlgorithm graphicsAlgorithm = shape.getGraphicsAlgorithm();
			if (!(graphicsAlgorithm instanceof RoundedRectangle)) {
				final int x = graphicsAlgorithm.getX();
				final int y = graphicsAlgorithm.getY();
				final int width = graphicsAlgorithm.getWidth();
				final int height = graphicsAlgorithm.getHeight();
				graphicsAlgorithm = gaService.createRoundedRectangle(shape, 12, 12);
				gaService.setLocationAndSize(graphicsAlgorithm, x, y, width, height);
			}
			final IColorConstant outlineColor = nodeOutlineColor(error);
			graphicsAlgorithm.setForeground(gaService.manageColor(currentDiagram, outlineColor.getRed(),
					outlineColor.getGreen(), outlineColor.getBlue()));
			graphicsAlgorithm.setBackground(gaService.manageColor(currentDiagram, color.get(0), color.get(1),
					color.get(2)));
			graphicsAlgorithm.setLineWidth(error ? 2 : 1);
			if (shape instanceof ContainerShape) {
				for (final Shape child : ((ContainerShape) shape).getChildren()) {
					if (child.getGraphicsAlgorithm() instanceof org.eclipse.graphiti.mm.algorithms.Image) {
						final String imageId = GamaImageProvider.nodeImageId(gamaObject);
						if (imageId != null) {
							((org.eclipse.graphiti.mm.algorithms.Image) child.getGraphicsAlgorithm()).setId(imageId);
						}
					}
				}
				LayoutCommonFeature.layout((ContainerShape) shape, currentDiagram, gamaObject, false);
			}
		}
		for (final Connection connection : currentDiagram.getConnections()) {
			RelationStyle.apply(currentDiagram, connection);
		}
	}

	/**
	 * Validation ended.
	 *
	 * @param model
	 *            the model
	 * @param experiments
	 *            the experiments
	 * @param status
	 *            the status
	 */
	@Override
	public void validationEnded(final IModelDescription model, final Iterable<? extends IDescription> experiments,
			final IValidationContext status) {
		wasOK = model != null && status != null && !status.hasErrors();
		final GamlEditorState snapshot = new GamlEditorState(model == null ? null : status,
				experiments == null ? List.of() : experiments);
		Display.getDefault().asyncExec(() -> {
			if (toolbar != null && !toolbar.isDisposed()) experimentToolbar.update(snapshot);
		});
		toRefresh = true;
	}

	/**
	 * Creates the part control XP.
	 *
	 * @param parent
	 *            the parent
	 */
	public void createPartControlXP(final Composite parent) {
		final GridLayout layout = new GridLayout(1, false);
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		parent.setLayout(layout);

		toolbar = new GamaToolbar2(parent, SWT.NONE);
		toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		experimentToolbar = new ExperimentToolbar(toolbar, this::launchExperiment,
				() -> ModelGenerator.modelValidation(getDiagramTypeProvider().getFeatureProvider(), getDiagram()),
				uri -> GAMA.getGui().getModelsManager().editModel(uri));

		// Asking the editor to fill the rest
		final Composite parent2 = new Composite(parent, SWT.NONE);
		parent2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		parent2.setLayout(new FillLayout());

		super.createPartControl(parent2);

	}

	private void launchExperiment(final String name) {
		final Diagram currentDiagram = getDiagram();
		final List<GamlCompilationError> validationErrors =
				ModelGenerator.modelValidation(getDiagramTypeProvider().getFeatureProvider(), currentDiagram);
		if (validationErrors == null || !wasOK) {
			MessageDialog.openError(getSite().getShell(), "Cannot run experiment",
					"Fix the model's validation errors before running an experiment.");
			return;
		}
		final IModelSpecies model =
				ModelGenerator.modelGeneration(getDiagramTypeProvider().getFeatureProvider(), currentDiagram);
		if (model == null) {
			MessageDialog.openError(getSite().getShell(), "Cannot run experiment",
					"The diagram could not be compiled. Check the model's validation errors.");
			return;
		}
		GAMA.runGuiExperiment(name, model);
	}

	@Override
	public void doSave(final IProgressMonitor monitor) {
		super.doSave(monitor);
	}

	/**
	 * Inits the gaml resource.
	 */
	public void initGamlResource() {
		final XtextResourceSet rs = new SynchronizedXtextResourceSet();
		rs.setClasspathURIContext(ModelGenerator.class);
		final URI uri = URI.createPlatformResourceURI("toto/" + getTitle() + ".gaml", true);
		resource = (GamlResource) rs.createResource(uri);
	}

	/**
	 * Gets the diagram.
	 *
	 * @return the diagram
	 */
	public Diagram getDiagram() {
		Diagram diag = diagram;
		if (diagram == null) {
			diag = this.getDiagramTypeProvider().getDiagram();
			diagram = diag;

		}
		return diag;
	}

	@Override
	public void initializeGraphicalViewer() {
		super.initializeGraphicalViewer();
		((GamaDiagramBehavior) getDiagramBehavior()).updateConnectionLayers();
		updateAppearanceIfNeeded();

		initGamlResource();
		if (getDiagram() != null) {
			ModelGenerator.modelValidation(getDiagramTypeProvider().getFeatureProvider(), getDiagram());
			doSave(null);
		}
	}

	@Override
	public void createPartControl(final Composite parent) {

		createPartControlXP(parent);
	}

	/**
	 * Gets the resource.
	 *
	 * @return the resource
	 */
	public GamlResource getResource() { return resource; }

	/**
	 * Sets the resource.
	 *
	 * @param resource
	 *            the new resource
	 */
	public void setResource(final GamlResource resource) {
		this.resource = resource;

	}

	/**
	 * Gets the errors.
	 *
	 * @return the errors
	 */
	public List<GamlCompilationError> getErrors() { return errors; }

	/**
	 * Sets the errors.
	 *
	 * @param errors
	 *            the new errors
	 */
	public void setErrors(final List<GamlCompilationError> errors/*
																	 * , ValidateStyledText vst
																	 */) {
		this.errors = errors;

		for (final GamlCompilationError error : errors) {
			final EObject toto = error.source();
			// System.out.println("syntaxErrorsLoc : " + syntaxErrorsLoc);*/
			final List<String> ids = new ArrayList<>();
			final String fist_obj = buildLocation(toto, ids);
			// System.out.println("location of error: " + ids);
			while (!ids.isEmpty()) {
				// System.out.println("idsEObjects.getKeys(): " +
				// idsEObjects.getKeys());
				if (idsEObjects.containsKey(ids)) { break; }
				ids.remove(ids.size() - 1);
			}
			Map<String, String> locs = errorsLoc.get(ids);

			if (locs == null) { locs = GamaMapFactory.create(); }
			// System.out.println("error.getCode() : " + error.getCode());
			final String key = "gaml.duplicate.definition.issue".equals(error.code())
					|| "gaml.duplicate.name.issue".equals(error.code()) ? "name" : fist_obj;
			locs.put(key, (locs.containsKey(key) ? locs.get(key) : "") + "\n" + error.toString());
			if ("Syntax errors detected ".equals(error.toString())) {
				if (syntaxErrorsLoc.isEmpty()) { syntaxErrorsLoc.put(ids, locs); }
			} else {
				errorsLoc.put(ids, locs);
			}

		}
		toRefresh = false;
		updateEObjectErrors();
	}

	/**
	 * Builds the location.
	 *
	 * @param toto
	 *            the toto
	 * @param ids
	 *            the ids
	 * @return the string
	 */
	public String buildLocation(EObject toto, final List<String> ids) {
		String fist_obj = null;
		do {
			// System.out.println("toto: " + toto);
			if (toto instanceof final S_ReflexImpl vv) {
				if (vv.getName() != null) { ids.add(0, vv.getName()); }
				if (fist_obj == null) {
					if (vv.getName() != null) {
						fist_obj = vv.getName();
					} else if (vv.getKey() != null) { fist_obj = vv.getKey(); }
				}
			} else if (toto instanceof final S_SpeciesImpl vv) {
				ids.add(0, vv.getName());
				if (fist_obj == null) { fist_obj = vv.getName(); }

			} else if (toto instanceof final FacetImpl vv) {
				// System.out.println("vv :" + vv.getKey() + ";");
				if (fist_obj == null && facets.contains(vv.getKey())) {
					fist_obj = vv.getKey();
					// System.out.println("fist_obj facet : " + fist_obj);
				}
			} else if (toto instanceof final S_ActionImpl vv) {
				ids.add(0, vv.getName());
				if (fist_obj == null) { fist_obj = vv.getName(); }
			} else if (toto instanceof final StatementImpl vv) {
				ids.add(0, vv.getKey());
				// System.out.println("vv:"+ vv.getKey());

				// System.out.println("vv.getFacets:"+ vv.getFacets());
			} else if (toto instanceof final S_DisplayImpl vv) {
				ids.add(0, vv.getName());
				if (fist_obj == null) { fist_obj = vv.getName(); }
			} else if (toto instanceof final BlockImpl vv) {
				/*
				 * System.out.println("block:" + vv ); System.out.println( "block getFunction:" + vv.getFunction() );
				 * System.out.println("block getStatements:" + vv.getStatements() );
				 */
				if (vv.getStatements() != null) {
					for (final Statement st : vv.getStatements()) {
						if (st != null && st.getKey() != null && "parameter".equals(st.getKey())) {
							// ids.add(0, st.getExpr().getOp());
							// System.out.println("st.getExpr " + st.getExpr());
							// System.out.println("st.getFacets " +
							// st.getFacets());

							// System.out.println("st.getBlock " +
							// st.getBlock());
						}
					}
				}

			} else if (toto instanceof final S_DefinitionImpl vv) {
				ids.add(0, vv.getName());
			} else if (toto instanceof final ArgumentDefinitionImpl vv) {
				ids.add(0, vv.getName());
			} else if (toto instanceof final S_ExperimentImpl vv) {
				ids.add(0, vv.getName());
				if (fist_obj == null) { fist_obj = vv.getName(); }
			} else if (toto instanceof final StatementImpl vv) {
				if ("text".equals(vv.getKey()) || "image".equals(vv.getKey()) || "chart".equals(vv.getKey())
						|| "draw".equals(vv.getKey())) {
					ids.add(0, vv.getKey());
				}
			} else if (toto instanceof VariableRefImpl) {
				// VariableRefImpl vv = (VariableRefImpl) toto;
				// System.out.println("var:" + vv );
				// System.out.println("var getRef:" + vv.getRef() );

				/*
				 * } else if (toto instanceof S_DefinitionImpl) { S_DefinitionImpl vv = (S_DefinitionImpl) toto;
				 * ids.add(0,vv.getName()); if (fist_obj == null) { fist_obj = vv.getName(); }
				 */
			} else if (toto instanceof final EGamaObject vv) {
				if (toto instanceof ELayer) {
					ids.add(0, ((ELayer) vv).getType() == null ? "species" : ((ELayer) vv).getType());
				} else if (toto instanceof ELayerAspect) {
					ids.add(0, "draw");
				} else {
					ids.add(0, vv.getName());
				}
				if (fist_obj == null) { fist_obj = vv.getName(); }
			}

			if (toto instanceof EAction) {
				toto = ((EAction) toto).getActionLinks().get(0).getSpecies();
			} else if (toto instanceof EReflex) {
				toto = ((EReflex) toto).getReflexLinks().get(0).getSpecies();
			} else if (toto instanceof ELayer) {
				toto = ((ELayer) toto).getDisplay();
			} else if (toto instanceof ELayerAspect) {
				toto = ((ELayerAspect) toto).getAspect();
			} else if (toto instanceof EAspect) {
				toto = ((EAspect) toto).getAspectLinks().get(0).getSpecies();
			} else if (toto instanceof EExperiment) {
				toto = ((EExperiment) toto).getExperimentLink().getSpecies();
			} else if (toto instanceof EDisplay) {
				toto = ((EDisplay) toto).getDisplayLink().getExperiment();
			} else if (toto instanceof ESpecies) {
				if (!"world".equals(((ESpecies) toto).getName())) {
					toto = ((ESpecies) toto).getMacroSpeciesLinks().get(0).getMacro();
				} else {
					toto = null;
				}
			} else {
				toto = toto != null ? toto.eContainer() : toto;
			}

		} while (toto != null && !(toto instanceof Model));
		if (!ids.contains("world")) { ids.add(0, "world"); }
		// System.out.println("ids: " + ids);
		return fist_obj;
	}

	/**
	 * Contain errors.
	 *
	 * @param location
	 *            the location
	 * @param name
	 *            the name
	 * @param uselessName
	 *            the useless name
	 * @return the string
	 */
	public String containErrors(final List<String> location, final String name, final List<String> uselessName) {
		// System.out.println("syntaxErrorsLoc: " + syntaxErrorsLoc);
		final boolean noName = "".equals(name);
		// System.out.println("location: " + location + " name: " + name);

		// System.out.println("errorsLoc: " + errorsLoc);
		if (errorsLoc == null || errorsLoc.isEmpty() || !errorsLoc.containsKey(location)) return "";

		if (noName) {
			final Map<String, String> er = errorsLoc.get(location);
			if (uselessName != null) { for (final String val : uselessName) { er.remove(val); } }
			// System.out.println("er 2: " + er);
			if (!er.isEmpty()) {
				final List<String> l = new ArrayList<>(er.values());
				return l.get(0);
			}
		} else if (errorsLoc.get(location).containsKey(name)) // System.out.println("errorsLoc.get(location).get(name):
																// " +
			// errorsLoc.get(location).get(name));
			return errorsLoc.get(location).get(name);

		return "";
	}

	/**
	 * Compute ids.
	 *
	 * @param obj
	 *            the obj
	 * @return the list
	 */
	public List<String> computeIds(final EObject obj) {
		EObject toto = obj;
		final List<String> ids = new ArrayList<>();
		do {
			if (toto != null) {
				if (toto instanceof EGamaObject) {
					if (toto instanceof ELayer) { ids.add(0, ((ELayer) toto).getType()); }
					if (toto instanceof ELayerAspect) {
						ids.add(0, "draw");
					} else {
						ids.add(0, ((EGamaObject) toto).getName());
					}
				}

				else if (toto instanceof EVariable) {
					ids.add(0, ((EVariable) toto).getName());
				} else if (toto instanceof EFacet) { ids.add(0, ((EFacet) toto).getName()); }
				if (toto instanceof EAction) {
					toto = ((EAction) toto).getActionLinks().get(0).getSpecies();
				} else if (toto instanceof EReflex) {
					toto = ((EReflex) toto).getReflexLinks().get(0).getSpecies();
				} else if (toto instanceof EAspect) {
					toto = ((EAspect) toto).getAspectLinks().get(0).getSpecies();
				} else if (toto instanceof EExperiment) {
					toto = ((EExperiment) toto).getExperimentLink().getSpecies();
				} else if (toto instanceof EDisplay) {
					toto = ((EDisplay) toto).getDisplayLink().getExperiment();
				} else if (toto instanceof ELayer) {
					toto = ((ELayer) toto).getDisplay();
				} else if (toto instanceof ELayerAspect) {
					toto = ((ELayerAspect) toto).getAspect();
				} else if (toto instanceof ESpecies) {
					if (!"world".equals(((ESpecies) toto).getName())) {
						toto = ((ESpecies) toto).getMacroSpeciesLinks().get(0).getMacro();
					} else {
						toto = null;
					}
				} else {
					toto = toto.eContainer();
				}
			}
		} while (toto != null && !(toto instanceof Model));
		if (!ids.contains("world")) { ids.add(0, "world"); }
		return ids;
	}

	/**
	 * Adds the E oject.
	 *
	 * @param obj
	 *            the obj
	 */
	public void addEOject(final EObject obj) {
		final List<String> ids = computeIds(obj);
		if (obj instanceof EVariable) {
			idsEObjects.put(ids, ((EVariable) obj).eContainer());
		} else if (obj instanceof EParameter) {
			idsEObjects.put(ids, ((EParameter) obj).eContainer());
		} else if (obj instanceof EFacet) {
			idsEObjects.put(ids, ((EFacet) obj).eContainer());
		} else if (obj instanceof EMonitor) {
			idsEObjects.put(ids, ((EMonitor) obj).eContainer());
		} else {
			idsEObjects.put(ids, obj);
		}

		// System.out.println("add object: " + obj + " ids: " + ids + "
		// idsEObjects: " + idsEObjects.keySet());
	}

	/**
	 * Adds the E oject.
	 *
	 * @param obj
	 *            the obj
	 * @param name
	 *            the name
	 */
	public void addEOject(final EObject obj, final String name) {
		final List<String> ids = computeIds(obj);
		ids.add(name);
		idsEObjects.put(ids, obj);
	}

	/**
	 * Removes the E oject.
	 *
	 * @param obj
	 *            the obj
	 */
	public void removeEOject(final EObject obj) {
		final List<String> ids = computeIds(obj);
		idsEObjects.remove(ids);
	}

	/**
	 * Update E object errors.
	 */
	public void updateEObjectErrors() {
		final TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(getDiagram());
		if (domain != null) {
			domain.getCommandStack().execute(new RecordingCommand(domain) {

				@Override
				public void doExecute() {

					initIdsEObjects();
					final List<List<String>> vals = new ArrayList<>(idsEObjects.keySet());
					final Map<List<String>, EObject> valM = GamaMapFactory.create();
					for (final EObject bo : valM.values()) {
						if (bo instanceof EGamaObject) {
							((EGamaObject) bo).setHasError(false);
						} else if (bo instanceof EVariable) { ((EVariable) bo).setHasError(false); }
					}
					for (final List<String> ids : vals) {
						final EObject obj = valM.get(ids);
						// System.out.println("ids: " + ids + " obj: " + obj);
						if (obj == null) { continue; }
						final boolean val = syntaxErrorsLoc.containsKey(ids) || errorsLoc.containsKey(ids);
						// System.out.println("val = " +val);
						if (val && obj instanceof EGamaObject) {
							((EGamaObject) obj).setHasError(val);
							// System.out.println(((EGamaObject)
							// obj).getHasError());
						}
					}
				}
			});
		}
	}

	/**
	 * Gets the errors loc.
	 *
	 * @return the errors loc
	 */
	public Map<List<String>, Map<String, String>> getErrorsLoc() { return errorsLoc; }

	/**
	 * Gets the syntax errors loc.
	 *
	 * @return the syntax errors loc
	 */
	public Map<List<String>, Map<String, String>> getSyntaxErrorsLoc() { return syntaxErrorsLoc; }

	/**
	 * Gets the ids E objects.
	 *
	 * @return the ids E objects
	 */
	public Map<List<String>, EObject> getIdsEObjects() { return idsEObjects; }

	/**
	 * Inits the ids E objects.
	 */
	public void initIdsEObjects() {
		// System.out.println("initIdsEObjects");
		if (diagram != null && !diagram.getChildren().isEmpty() && idsEObjects.isEmpty()) {
			for (final Shape obj : getDiagram().getChildren()) {
				final Object bo =
						getDiagramTypeProvider().getFeatureProvider().getBusinessObjectForPictogramElement(obj);
				// System.out.println("obj : " + bo);
				if (bo instanceof EObject) {
					addEOject((EObject) bo);

				}
				if (bo instanceof ESpecies) {
					boolean shape = false;
					boolean location = false;
					for (final EVariable v : ((ESpecies) bo).getVariables()) {
						addEOject(v);
						if (location || "location".equals(v.getName())) { location = true; }
						if (shape || "shape".equals(v.getName())) { shape = true; }
					}
					if (!shape) { addEOject((EObject) bo, "shape"); }
					if (!location) { addEOject((EObject) bo, "location"); }
				} else if (bo instanceof EAspect) {
					addEOject((EObject) bo, "draw");
				} else if (bo instanceof EDisplay) {
					for (final ELayer lay : ((EDisplay) bo).getLayers()) { addEOject((EObject) bo, lay.getType()); }
				} else if (bo instanceof EExperiment) {
					for (final EParameter v : ((EExperiment) bo).getParameters()) { addEOject(v); }
					for (final EMonitor v : ((EExperiment) bo).getMonitors()) { addEOject(v); }
				}
			}
		}
	}

	/**
	 * Update errors.
	 *
	 * @param oldId
	 *            the old id
	 * @param newId
	 *            the new id
	 */
	public void updateErrors(final List<String> oldId, final List<String> newId) {
		final Map<String, String> eMap = errorsLoc.remove(oldId);
		if (eMap != null) { errorsLoc.put(newId, eMap); }

		final Map<String, String> eMap2 = syntaxErrorsLoc.remove(oldId);
		if (eMap2 != null) { syntaxErrorsLoc.put(newId, eMap2); }

		final EObject obj = idsEObjects.remove(oldId);
		if (obj != null) { idsEObjects.put(newId, obj); }
	}

	/**
	 * Checks if is was OK.
	 *
	 * @return true, if is was OK
	 */
	public boolean isWasOK() { return wasOK; }

	@Override
	protected DiagramBehavior createDiagramBehavior() {
		return new GamaDiagramBehavior(this);
	}

}
