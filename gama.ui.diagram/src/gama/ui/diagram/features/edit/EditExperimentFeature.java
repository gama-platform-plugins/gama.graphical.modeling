package gama.ui.diagram.features.edit;

import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICustomContext;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;

import gama.ui.diagram.editor.MyGamaToolBehaviorProvider;
import gama.ui.diagram.features.ExampleUtil;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.swt.editFrame.ActionWizardAdapter;
import gama.ui.diagram.swt.editFrame.EditFrame;
import gama.ui.diagram.swt.editFrame.ExperimentWizardAdapter;
import gama.ui.diagram.swt.editFrame.SpeciesWizardAdapter;
import gaml.compiler.ui.wizards.experiment.ExperimentWizard;

public class EditExperimentFeature extends EditFeature {
	public EditExperimentFeature(final IFeatureProvider fp, final EditFrame frame, final MyGamaToolBehaviorProvider tbp) {
		super(fp, frame, tbp);
	}

	@Override public String getDescription() { return "Edit an experiment"; }

	@Override
	public boolean canExecute(final ICustomContext context) {
		final var elements = context.getPictogramElements();
		return elements != null && elements.length == 1
				&& getBusinessObjectForPictogramElement(elements[0]) instanceof EExperiment;
	}

	@Override
	public void execute(final ICustomContext context) {
		hasDoneChanges = false;
		if (!canExecute(context)) return;
		final var shape = context.getPictogramElements()[0];
		final var experiment = (EExperiment) getBusinessObjectForPictogramElement(shape);
		final var diagram = getDiagram();
		final var editor = ExampleUtil.getDiagramEditor(getFeatureProvider());
		final var initial = ExperimentWizardAdapter.snapshot(experiment);
		final String originalSource = ModelGenerator.generateModel(getFeatureProvider(), diagram, null);
		final var world = (EWorldAgent) experiment.getExperimentLink().getSpecies();
		final var wizard = new ExperimentWizard(editor.getSite().getShell(), initial,
				draft -> ExperimentWizardAdapter.validate(getFeatureProvider(), diagram, experiment, null, draft),
				ActionWizardAdapter.typeNames(diagram), () -> ExperimentWizardAdapter.variableNames(experiment, world),
				draft -> ExperimentWizardAdapter.bodyContext(getFeatureProvider(), diagram, experiment, null, draft),
				() -> ExperimentWizardAdapter.parentNames(diagram, experiment));
		if (wizard.open() != Window.OK || initial.equals(wizard.getResult())) return;
		if (experiment.eResource() != diagram.eResource()
				|| !initial.equals(ExperimentWizardAdapter.snapshot(experiment))
				|| !originalSource.equals(ModelGenerator.generateModel(getFeatureProvider(), diagram, null))) {
			MessageDialog.openError(editor.getSite().getShell(), "Experiment context changed",
					"The experiment or model changed while the wizard was open. Reopen it before applying edits.");
			return;
		}
		final var domain = TransactionUtil.getEditingDomain(experiment);
		if (domain == null) {
			MessageDialog.openError(editor.getSite().getShell(), "Cannot edit experiment",
					"The experiment is not attached to an editable diagram.");
			return;
		}
		domain.getCommandStack().execute(new RecordingCommand(domain, "Edit experiment") {
			@Override protected void doExecute() {
				final var previous = SpeciesWizardAdapter.parentConnections(diagram, experiment);
				ExperimentWizardAdapter.apply(experiment, wizard.getResult());
				SpeciesWizardAdapter.syncParentConnection(getFeatureProvider(), diagram, experiment, previous);
				updatePictogramElement(shape);
				layoutPictogramElement(shape);
				ModelGenerator.modelValidation(getFeatureProvider(), diagram);
			}
		});
		hasDoneChanges = true;
	}
}
