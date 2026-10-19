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
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.swt.editFrame.BehaviorWizardAdapter;
import gama.ui.diagram.swt.editFrame.DiagramBehavior;
import gama.ui.diagram.swt.editFrame.EditFrame;
import gaml.compiler.ui.wizards.behavior.BehaviorWizard;

public class EditBehaviorFeature extends EditFeature {
	private final DiagramBehavior kind;

	protected EditBehaviorFeature(final IFeatureProvider fp, final EditFrame frame,
			final MyGamaToolBehaviorProvider tbp, final DiagramBehavior kind) {
		super(fp, frame, tbp);
		this.kind = kind;
	}

	@Override
	public String getDescription() { return "Edit a " + kind.keyword(); }

	@Override
	public boolean canExecute(final ICustomContext context) {
		final var elements = context.getPictogramElements();
		return elements != null && elements.length == 1
				&& getBusinessObjectForPictogramElement(elements[0]) instanceof EGamaObject object
				&& kind.matches(object);
	}

	@Override
	public void execute(final ICustomContext context) {
		hasDoneChanges = false;
		if (!canExecute(context)) return;
		final var shape = context.getPictogramElements()[0];
		final var object = (EGamaObject) getBusinessObjectForPictogramElement(shape);
		final var editor = ExampleUtil.getDiagramEditor(getFeatureProvider());
		final var diagram = getDiagram();
		final var initial = BehaviorWizardAdapter.snapshot(object);
		final String originalSource = ModelGenerator.generateModel(getFeatureProvider(), diagram, null);
		final var wizard = new BehaviorWizard(editor.getSite().getShell(), initial,
				BehaviorWizardAdapter.selectableKinds(kind),
				draft -> BehaviorWizardAdapter.validate(getFeatureProvider(), diagram, object, null, draft),
				draft -> BehaviorWizardAdapter.bodyContext(getFeatureProvider(), diagram, object, null, draft));
		wizard.setCreationMode(false);
		if (wizard.open() != Window.OK || initial.equals(wizard.getResult())) return;
		if (object.eResource() != diagram.eResource() || !initial.equals(BehaviorWizardAdapter.snapshot(object))
				|| !originalSource.equals(ModelGenerator.generateModel(getFeatureProvider(), diagram, null))) {
			MessageDialog.openError(editor.getSite().getShell(), "Behavior context changed",
					"The behavior or its model changed while the wizard was open. Reopen it before applying edits.");
			return;
		}
		final var domain = TransactionUtil.getEditingDomain(object);
		if (domain == null) {
			MessageDialog.openError(editor.getSite().getShell(), "Cannot edit behavior",
					"The behavior is not attached to an editable diagram.");
			return;
		}
		domain.getCommandStack().execute(new RecordingCommand(domain, "Edit " + kind.keyword()) {
			@Override protected void doExecute() {
				BehaviorWizardAdapter.apply(object, wizard.getResult());
				updatePictogramElement(shape);
				layoutPictogramElement(shape);
				if (kind.lifecycle()) {
					for (final var relation : kind.links(object)) {
						for (final var element : org.eclipse.graphiti.services.Graphiti.getLinkService()
								.getPictogramElements(diagram, relation)) {
							if (element instanceof org.eclipse.graphiti.mm.pictograms.Connection connection) {
								gama.ui.diagram.features.add.RelationStyle.apply(diagram, connection);
							}
						}
					}
				}
				ModelGenerator.modelValidation(getFeatureProvider(), diagram);
			}
		});
		hasDoneChanges = true;
	}
}
