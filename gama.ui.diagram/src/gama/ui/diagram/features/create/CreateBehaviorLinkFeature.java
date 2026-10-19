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
import gama.ui.diagram.swt.editFrame.BehaviorWizardAdapter;
import gama.ui.diagram.swt.editFrame.DiagramBehavior;
import gaml.compiler.ui.wizards.behavior.BehaviorDraft;
import gaml.compiler.ui.wizards.behavior.BehaviorKind;
import gaml.compiler.ui.wizards.behavior.BehaviorWizard;

public abstract class CreateBehaviorLinkFeature extends AbstractCreateSpeciesComponentLinkFeature {
	private final DiagramBehavior kind;
	private final String imageId;
	private boolean changed;

	protected CreateBehaviorLinkFeature(final IFeatureProvider fp, final DiagramBehavior kind, final String imageId) {
		super(fp, kind == DiagramBehavior.REFLEX ? "Add reflex..." : "has the " + kind.keyword(),
				kind.lifecycle() ? "Create a reflex, init or abort behavior" : "Create " + kind.keyword() + " link");
		this.kind = kind;
		this.imageId = imageId;
	}

	@Override public String getCreateImageId() { return imageId; }
	@Override public boolean hasDoneChanges() { return changed; }

	@Override
	public Connection create(final ICreateConnectionContext context) {
		changed = false;
		if (!canCreate(context)) return null;
		final var source = getESpecies(context.getSourceAnchor());
		final var diagram = getDiagram();
		final var editor = ExampleUtil.getDiagramEditor(getFeatureProvider());
		final String originalSource = ModelGenerator.generateModel(getFeatureProvider(), diagram, null);
		final var wizard = new BehaviorWizard(editor.getSite().getShell(),
				BehaviorDraft.empty(BehaviorKind.of(kind.keyword())),
				BehaviorWizardAdapter.selectableKinds(kind),
				draft -> BehaviorWizardAdapter.validate(getFeatureProvider(), diagram, null, source, draft),
				draft -> BehaviorWizardAdapter.bodyContext(getFeatureProvider(), diagram, null, source, draft));
		if (wizard.open() != Window.OK) return null;
		if (source.eResource() != diagram.eResource()
				|| !originalSource.equals(ModelGenerator.generateModel(getFeatureProvider(), diagram, null))) {
			MessageDialog.openError(editor.getSite().getShell(), "Behavior context changed",
					"The model changed while the wizard was open. Reopen it before creating the behavior.");
			return null;
		}
		final var selectedKind = DiagramBehavior.valueOf(wizard.getResult().kind().name());
		final var target = selectedKind.create();
		BehaviorWizardAdapter.apply(target, wizard.getResult());
		final var relation = selectedKind.attach(source, target);
		final var create = new CreateContext();
		create.setLocation(context.getTargetLocation().getX(), context.getTargetLocation().getY());
		create.setTargetContainer(diagram);
		final var shape = getFeatureProvider().addIfPossible(new AddContext(create, target));
		if (shape == null) throw new IllegalStateException("Could not draw the new " + kind.keyword() + ".");
		final var box = shape.getGraphicsAlgorithm();
		Graphiti.getGaService().setLocation(box, context.getTargetLocation().getX() - box.getWidth() / 2,
				context.getTargetLocation().getY() - box.getHeight() / 2);
		final var add = new AddConnectionContext(context.getSourceAnchor(), getAnchor(shape));
		add.setNewObject(relation);
		final var connection = (Connection) getFeatureProvider().addIfPossible(add);
		if (connection == null) throw new IllegalStateException("Could not draw the behavior link.");
		((GamaDiagramEditor) editor).addEOject(target);
		ModelGenerator.modelValidation(getFeatureProvider(), diagram);
		changed = true;
		return connection;
	}
}
