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
import gama.ui.diagram.metamodel.EAction;
import gama.ui.diagram.swt.editFrame.ActionWizardAdapter;
import gama.ui.diagram.swt.editFrame.EditFrame;
import gaml.compiler.ui.wizards.action.ActionWizard;

public class EditActionFeature extends EditFeature {
	public EditActionFeature(final IFeatureProvider fp, final EditFrame frame, final MyGamaToolBehaviorProvider tbp) {
		super(fp, frame, tbp);
	}

	@Override
	public String getDescription() {
		return "Edit an action";
	}

	@Override
	public boolean canExecute(final ICustomContext context) {
		final var elements = context.getPictogramElements();
		return elements != null && elements.length == 1
				&& getBusinessObjectForPictogramElement(elements[0]) instanceof EAction;
	}

	@Override
	public void execute(final ICustomContext context) {
		hasDoneChanges = false;
		if (!canExecute(context)) return;
		final var shape = context.getPictogramElements()[0];
		final var action = (EAction) getBusinessObjectForPictogramElement(shape);
		final var diagram = getDiagram();
		final var editor = ExampleUtil.getDiagramEditor(getFeatureProvider());
		final var initial = ActionWizardAdapter.snapshot(action);
		final String originalSource = ModelGenerator.generateModel(getFeatureProvider(), diagram, null);
		final var wizard = new ActionWizard(editor.getSite().getShell(), initial,
				draft -> ActionWizardAdapter.validate(getFeatureProvider(), diagram, action, null, draft),
				ActionWizardAdapter.typeNames(diagram),
				draft -> ActionWizardAdapter.bodyContext(getFeatureProvider(), diagram, action, null, draft));
		wizard.setCreationMode(false);
		if (wizard.open() != Window.OK || initial.equals(wizard.getResult())) return;
		if (action.eResource() != diagram.eResource() || !initial.equals(ActionWizardAdapter.snapshot(action))
				|| !originalSource.equals(ModelGenerator.generateModel(getFeatureProvider(), diagram, null))) {
			MessageDialog.openError(editor.getSite().getShell(), "Action context changed",
					"The action or its model changed while the wizard was open. Reopen it before applying edits.");
			return;
		}
		final var domain = TransactionUtil.getEditingDomain(action);
		if (domain == null) {
			MessageDialog.openError(editor.getSite().getShell(), "Cannot edit action",
					"The action is not attached to an editable diagram.");
			return;
		}
		domain.getCommandStack().execute(new RecordingCommand(domain, "Edit action") {
			@Override
			protected void doExecute() {
				ActionWizardAdapter.apply(action, wizard.getResult());
				updatePictogramElement(shape);
				layoutPictogramElement(shape);
				ModelGenerator.modelValidation(getFeatureProvider(), diagram);
			}
		});
		hasDoneChanges = true;
	}
}
