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
import gama.ui.diagram.metamodel.EDisplay;
import gama.ui.diagram.swt.editFrame.DisplayWizardAdapter;
import gama.ui.diagram.swt.editFrame.EditFrame;
import gaml.compiler.ui.wizards.display.DisplayDraft;
import gaml.compiler.ui.wizards.display.DisplayWizard;

public class EditDisplayFeature extends EditFeature {
	public EditDisplayFeature(final IFeatureProvider fp, final EditFrame frame, final MyGamaToolBehaviorProvider tbp) {
		super(fp, frame, tbp);
	}

	@Override public String getDescription() { return "Edit display settings and layers"; }

	@Override
	public boolean canExecute(final ICustomContext context) {
		final var elements = context.getPictogramElements();
		return elements != null && elements.length == 1
				&& getBusinessObjectForPictogramElement(elements[0]) instanceof EDisplay;
	}

	@Override
	public void execute(final ICustomContext context) {
		hasDoneChanges = false;
		if (!canExecute(context)) return;
		final var shape = context.getPictogramElements()[0];
		final var display = (EDisplay) getBusinessObjectForPictogramElement(shape);
		final var editor = ExampleUtil.getDiagramEditor(getFeatureProvider());
		final var diagram = getDiagram();
		final DisplayDraft initial;
		try {
			initial = DisplayWizardAdapter.snapshot(display);
		} catch (final IllegalArgumentException error) {
			MessageDialog.openError(editor.getSite().getShell(), "Cannot edit display", error.getMessage());
			return;
		}
		final String originalSource = ModelGenerator.generateModel(getFeatureProvider(), diagram, null);
		final var wizard = new DisplayWizard(editor.getSite().getShell(), initial,
				draft -> DisplayWizardAdapter.validate(getFeatureProvider(), diagram, display, null, draft),
				() -> DisplayWizardAdapter.speciesNames(diagram, false),
				() -> DisplayWizardAdapter.speciesNames(diagram, true),
				draft -> DisplayWizardAdapter.context(getFeatureProvider(), diagram, display, null, draft),
				species -> DisplayWizardAdapter.aspectNames(diagram, species));
		wizard.setCreationMode(false);
		if (wizard.open() != Window.OK || initial.equals(wizard.getResult())) return;
		if (display.eResource() != diagram.eResource() || !initial.equals(DisplayWizardAdapter.snapshot(display))
				|| !originalSource.equals(ModelGenerator.generateModel(getFeatureProvider(), diagram, null))) {
			MessageDialog.openError(editor.getSite().getShell(), "Display context changed",
					"The display or its model changed while the wizard was open. Reopen it before applying edits.");
			return;
		}
		final var domain = TransactionUtil.getEditingDomain(display);
		if (domain == null) {
			MessageDialog.openError(editor.getSite().getShell(), "Cannot edit display",
					"The display is not attached to an editable diagram.");
			return;
		}
		domain.getCommandStack().execute(new RecordingCommand(domain, "Edit display") {
			@Override protected void doExecute() {
				DisplayWizardAdapter.apply(display, wizard.getResult());
				updatePictogramElement(shape);
				layoutPictogramElement(shape);
				ModelGenerator.modelValidation(getFeatureProvider(), diagram);
			}
		});
		hasDoneChanges = true;
	}
}
