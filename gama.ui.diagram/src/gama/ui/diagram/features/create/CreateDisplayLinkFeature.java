package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICreateConnectionContext;
import org.eclipse.graphiti.features.context.impl.AddConnectionContext;
import org.eclipse.graphiti.features.context.impl.AddContext;
import org.eclipse.graphiti.features.context.impl.CreateContext;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;

import gama.ui.diagram.editor.GamaDiagramEditor;
import gama.ui.diagram.features.ExampleUtil;
import gama.ui.diagram.features.modelgeneration.ExperimentKind;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.GamaFactory;
import gama.ui.diagram.swt.editFrame.DisplayWizardAdapter;
import gama.ui.diagram.swt.image.GamaImageProvider;
import gaml.compiler.ui.wizards.display.DisplayDraft;
import gaml.compiler.ui.wizards.display.DisplayWizard;

public class CreateDisplayLinkFeature extends AbstractCreateSpeciesComponentLinkFeature {
	private boolean changed;

	public CreateDisplayLinkFeature(final IFeatureProvider fp) {
		super(fp, "Add display...", "Create display settings and layers");
	}

	@Override public String getCreateImageId() { return GamaImageProvider.IMG_DISPLAYLINK; }
	@Override public boolean hasDoneChanges() { return changed; }

	@Override
	public boolean canCreate(final ICreateConnectionContext context) {
		return getESpecies(context.getSourceAnchor()) instanceof EExperiment experiment
				&& "gui".equals(ExperimentKind.of(experiment));
	}

	@Override public boolean canStartConnection(final ICreateConnectionContext context) { return canCreate(context); }
	@Override public Connection create(final ICreateConnectionContext context) { return create(context, true); }

	public Connection create(final ICreateConnectionContext context, final boolean ask) {
		changed = false;
		if (!canCreate(context)) return null;
		final var experiment = (EExperiment) getESpecies(context.getSourceAnchor());
		final var diagram = getDiagram();
		final var editor = ExampleUtil.getDiagramEditor(getFeatureProvider());
		final String originalSource = ModelGenerator.generateModel(getFeatureProvider(), diagram, null);
		DisplayDraft draft = DisplayDraft.empty();
		if (ask) {
			final var wizard = new DisplayWizard(editor.getSite().getShell(), draft,
					value -> DisplayWizardAdapter.validate(getFeatureProvider(), diagram, null, experiment, value),
					() -> DisplayWizardAdapter.speciesNames(diagram, false),
					() -> DisplayWizardAdapter.speciesNames(diagram, true),
					value -> DisplayWizardAdapter.context(getFeatureProvider(), diagram, null, experiment, value),
					species -> DisplayWizardAdapter.aspectNames(diagram, species));
			wizard.setCreationMode(true);
			if (wizard.open() != Window.OK) return null;
			draft = wizard.getResult();
			if (experiment.eResource() != diagram.eResource()
					|| !originalSource.equals(ModelGenerator.generateModel(getFeatureProvider(), diagram, null))) {
				MessageDialog.openError(editor.getSite().getShell(), "Display context changed",
						"The model changed while the wizard was open. Reopen it before creating the display.");
				return null;
			}
		}
		final var target = GamaFactory.eINSTANCE.createEDisplay();
		final var relation = DisplayWizardAdapter.attach(experiment, target);
		DisplayWizardAdapter.apply(target, draft);
		final var create = new CreateContext();
		create.setTargetContainer(diagram);
		create.setLocation(context.getTargetLocation().getX(), context.getTargetLocation().getY());
		final var shape = getFeatureProvider().addIfPossible(new AddContext(create, target));
		if (shape == null) throw new IllegalStateException("Could not draw the display.");
		final var box = shape.getGraphicsAlgorithm();
		Graphiti.getGaService().setLocation(box, context.getTargetLocation().getX() - box.getWidth() / 2,
				context.getTargetLocation().getY() - box.getHeight() / 2);
		final var add = new AddConnectionContext(context.getSourceAnchor(), getAnchor(shape));
		add.setNewObject(relation);
		final var connection = (Connection) getFeatureProvider().addIfPossible(add);
		if (connection == null) throw new IllegalStateException("Could not draw the display link.");
		((GamaDiagramEditor) editor).addEOject(target);
		ModelGenerator.modelValidation(getFeatureProvider(), diagram);
		changed = true;
		return connection;
	}
}
