/*******************************************************************************************************
 *
 * CreateActionLinkFeature.java, in gama.ui.diagram, is part of the source code of the GAMA modeling and simulation
 * platform (v.1.9.3).
 *
 * (c) 2007-2024 UMI 209 UMMISCO IRD/SU & Partners (IRIT, MIAT, TLU, CTU)
 *
 * Visit https://github.com/gama-platform/gama for license information and contacts.
 *
 ********************************************************************************************************/
package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICreateConnectionContext;
import org.eclipse.graphiti.features.context.impl.AddConnectionContext;
import org.eclipse.graphiti.features.context.impl.AddContext;
import org.eclipse.graphiti.features.context.impl.CreateContext;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;

import gama.ui.diagram.editor.GamaDiagramEditor;
import gama.ui.diagram.features.ExampleUtil;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EAction;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.swt.image.GamaImageProvider;
import gama.ui.diagram.swt.editFrame.ActionWizardAdapter;
import gaml.compiler.ui.wizards.action.ActionDraft;
import gaml.compiler.ui.wizards.action.ActionWizard;

/**
 * The Class CreateActionLinkFeature.
 */
public class CreateActionLinkFeature extends AbstractCreateSpeciesComponentLinkFeature {
	private boolean hasDoneChanges;

	/**
	 * Instantiates a new creates the action link feature.
	 *
	 * @param fp
	 *            the fp
	 */
	public CreateActionLinkFeature(final IFeatureProvider fp) {
		// provide name and description for the UI, e.g. the palette
		super(fp, "Add action...", "Create action link");
	}

	@Override
	protected boolean acceptsClassSource(final ESpecies source) {
		return true;
	}

	/**
	 * Creates the E action.
	 *
	 * @param source
	 *            the source
	 * @return the e action
	 */
	private EAction createEAction(final ESpecies source) {
		final var editor = ExampleUtil.getDiagramEditor(getFeatureProvider());
		final var diagram = getDiagram();
		final String originalSource = ModelGenerator.generateModel(getFeatureProvider(), diagram, null);
		final var wizard = new ActionWizard(editor.getSite().getShell(), ActionDraft.empty(),
				draft -> ActionWizardAdapter.validate(getFeatureProvider(), diagram, null, source, draft),
				ActionWizardAdapter.typeNames(diagram),
				draft -> ActionWizardAdapter.bodyContext(getFeatureProvider(), diagram, null, source, draft));
		wizard.setCreationMode(true);
		if (wizard.open() != Window.OK) return null;
		if (source.eResource() != diagram.eResource()
				|| !originalSource.equals(ModelGenerator.generateModel(getFeatureProvider(), diagram, null))) {
			MessageDialog.openError(editor.getSite().getShell(), "Action context changed",
					"The model changed while the wizard was open. Reopen it before creating the action.");
			return null;
		}
		EAction newAction = gama.ui.diagram.metamodel.GamaFactory.eINSTANCE.createEAction();
		newAction.setError("");
		newAction.setHasError(false);
		ActionWizardAdapter.apply(newAction, wizard.getResult());
		return newAction;
	}

	/**
	 * Adds the E action.
	 *
	 * @param context
	 *            the context
	 * @param action
	 *            the action
	 * @return the pictogram element
	 */
	private PictogramElement addEAction(final ICreateConnectionContext context, final EAction action) {
		CreateContext cc = new CreateContext();
		cc.setLocation(context.getTargetLocation().getX(), context.getTargetLocation().getY());
		cc.setSize(0, 0);
		cc.setTargetContainer(getDiagram());
		final var shape = getFeatureProvider().addIfPossible(new AddContext(cc, action));
		if (shape == null) throw new IllegalStateException("Could not draw the new action.");
		final var box = shape.getGraphicsAlgorithm();
		Graphiti.getGaService().setLocation(box, context.getTargetLocation().getX() - box.getWidth() / 2,
				context.getTargetLocation().getY() - box.getHeight() / 2);
		return shape;
	}

	@Override
	public Connection create(final ICreateConnectionContext context) {
		hasDoneChanges = false;
		if (!canCreate(context)) return null;
		final ESpecies source = getESpecies(context.getSourceAnchor());
		final EAction target = createEAction(source);
		if (target == null) return null;
		final var relation = ActionWizardAdapter.attach(source, target);
		final var shape = addEAction(context, target);
		final var addContext = new AddConnectionContext(context.getSourceAnchor(), getAnchor(shape));
		addContext.setNewObject(relation);
		final var newConnection = (Connection) getFeatureProvider().addIfPossible(addContext);
		if (newConnection == null) throw new IllegalStateException("Could not draw the new action link.");
		GamaDiagramEditor diagramEditor = (GamaDiagramEditor) ExampleUtil.getDiagramEditor(getFeatureProvider());
		diagramEditor.addEOject(target);
		ModelGenerator.modelValidation(getFeatureProvider(), getDiagram());
		hasDoneChanges = true;
		return newConnection;
	}

	@Override
	public boolean hasDoneChanges() { return hasDoneChanges; }

	@Override
	public String getCreateImageId() { return GamaImageProvider.IMG_ACTIONLINK; }

}
