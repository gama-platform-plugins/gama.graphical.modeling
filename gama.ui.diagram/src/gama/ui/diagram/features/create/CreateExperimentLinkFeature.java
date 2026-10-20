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
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.metamodel.GamaFactory;
import gama.ui.diagram.swt.editFrame.ActionWizardAdapter;
import gama.ui.diagram.swt.editFrame.ExperimentWizardAdapter;
import gama.ui.diagram.swt.editFrame.SpeciesWizardAdapter;
import gaml.compiler.ui.wizards.experiment.ExperimentDraft;
import gaml.compiler.ui.wizards.experiment.ExperimentWizard;

public abstract class CreateExperimentLinkFeature extends AbstractCreateSpeciesComponentLinkFeature {
	private final String type;
	private final String image;
	private boolean changed;

	protected CreateExperimentLinkFeature(final IFeatureProvider fp, final String type, final String image) {
		super(fp, "gui".equals(type) ? "Add experiment..." : "Add " + type + " experiment...",
				"Create a new experiment");
		this.type = type;
		this.image = image;
	}

	@Override public String getCreateImageId() { return image; }
	@Override public boolean hasDoneChanges() { return changed; }

	@Override
	public boolean canCreate(final ICreateConnectionContext context) {
		return getESpecies(context.getSourceAnchor()) instanceof EWorldAgent;
	}

	@Override public boolean canStartConnection(final ICreateConnectionContext context) { return canCreate(context); }

	@Override public Connection create(final ICreateConnectionContext context) { return create(context, true); }

	public Connection create(final ICreateConnectionContext context, final boolean ask) {
		changed = false;
		if (!canCreate(context)) return null;
		final var world = (EWorldAgent) getESpecies(context.getSourceAnchor());
		final var diagram = getDiagram();
		final var editor = ExampleUtil.getDiagramEditor(getFeatureProvider());
		final String originalSource = ModelGenerator.generateModel(getFeatureProvider(), diagram, null);
		ExperimentDraft draft = ExperimentDraft.empty(type);
		if (ask) {
			final var wizard = new ExperimentWizard(editor.getSite().getShell(), draft,
					value -> ExperimentWizardAdapter.validate(getFeatureProvider(), diagram, null, world, value),
					ActionWizardAdapter.typeNames(diagram), () -> ExperimentWizardAdapter.variableNames(null, world),
					value -> ExperimentWizardAdapter.bodyContext(getFeatureProvider(), diagram, null, world, value),
					() -> ExperimentWizardAdapter.parentNames(diagram, null));
			wizard.setCreationMode(true);
			if (wizard.open() != Window.OK) return null;
			draft = wizard.getResult();
			if (world.eResource() != diagram.eResource()
					|| !originalSource.equals(ModelGenerator.generateModel(getFeatureProvider(), diagram, null))) {
				MessageDialog.openError(editor.getSite().getShell(), "Experiment context changed",
						"The model changed while the wizard was open. Reopen it before creating the experiment.");
				return null;
			}
		}
		final var target = GamaFactory.eINSTANCE.createEGUIExperiment();
		final var relation = ExperimentWizardAdapter.attach(world, target);
		ExperimentWizardAdapter.apply(target, draft);
		final var create = new CreateContext();
		create.setTargetContainer(diagram);
		create.setLocation(context.getTargetLocation().getX(), context.getTargetLocation().getY());
		final var shape = getFeatureProvider().addIfPossible(new AddContext(create, target));
		if (shape == null) throw new IllegalStateException("Could not draw the experiment.");
		final var box = shape.getGraphicsAlgorithm();
		Graphiti.getGaService().setLocation(box, context.getTargetLocation().getX() - box.getWidth() / 2,
				context.getTargetLocation().getY() - box.getHeight() / 2);
		final var add = new AddConnectionContext(context.getSourceAnchor(), getAnchor(shape));
		add.setNewObject(relation);
		final var connection = (Connection) getFeatureProvider().addIfPossible(add);
		if (connection == null) throw new IllegalStateException("Could not draw the experiment link.");
		SpeciesWizardAdapter.syncParentConnection(getFeatureProvider(), diagram, target, java.util.List.of());
		((GamaDiagramEditor) editor).addEOject(target);
		ModelGenerator.modelValidation(getFeatureProvider(), diagram);
		changed = true;
		return connection;
	}
}
