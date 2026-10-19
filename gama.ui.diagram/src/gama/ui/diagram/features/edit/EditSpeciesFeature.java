package gama.ui.diagram.features.edit;

import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICustomContext;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;

import gama.ui.diagram.editor.MyGamaToolBehaviorProvider;
import gama.ui.diagram.features.ExampleUtil;
import gama.ui.diagram.features.modelgeneration.DiagramModelDocumentation;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EGrid;
import gama.ui.diagram.metamodel.EClassSpecies;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.swt.editFrame.EditFrame;
import gama.ui.diagram.swt.editFrame.SpeciesWizardAdapter;
import gaml.compiler.ui.wizards.species.ModelLocation;
import gaml.compiler.ui.wizards.species.SpeciesWizard;

public class EditSpeciesFeature extends EditFeature {
	public EditSpeciesFeature(final IFeatureProvider fp, final EditFrame frame, final MyGamaToolBehaviorProvider tbp) {
		super(fp, frame, tbp);
	}

	@Override
	public String getDescription() {
		return "Edit a species, grid, class or model";
	}

	@Override
	public boolean canExecute(final ICustomContext context) {
		final var elements = context.getPictogramElements();
		return elements != null && elements.length == 1
				&& getBusinessObjectForPictogramElement(elements[0]) instanceof ESpecies;
	}

	@Override
	public void execute(final ICustomContext context) {
		hasDoneChanges = false;
		if (!canExecute(context)) return;
		final var shape = context.getPictogramElements()[0];
		final var species = (ESpecies) getBusinessObjectForPictogramElement(shape);
		if (species instanceof EWorldAgent world) {
			editWorld(world, shape);
			return;
		}
		final var editor = ExampleUtil.getDiagramEditor(getFeatureProvider());
		final var initial = SpeciesWizardAdapter.snapshot(species);
		final var wizard = new SpeciesWizard(editor.getSite().getShell(), initial,
				draft -> SpeciesWizardAdapter.validate(getFeatureProvider(), getDiagram(), species, null, draft),
				true, SpeciesWizardAdapter.parentNames(species, species), SpeciesWizardAdapter.wizardContext(species),
				species instanceof EClassSpecies ? SpeciesWizard.Kind.CLASS
						: species instanceof EGrid ? SpeciesWizard.Kind.GRID : SpeciesWizard.Kind.SPECIES);
		wizard.setCreationMode(false);
		if (wizard.open() != Window.OK || initial.equals(wizard.getResult())) return;
		if (!initial.equals(SpeciesWizardAdapter.snapshot(species))) {
			MessageDialog.openError(editor.getSite().getShell(), "Species changed",
					"The species changed while the wizard was open. Reopen it before applying edits.");
			return;
		}
		final var domain = TransactionUtil.getEditingDomain(species);
		if (domain == null) {
			MessageDialog.openError(editor.getSite().getShell(), "Cannot edit species",
					"The species is not attached to an editable diagram.");
			return;
		}
		domain.getCommandStack().execute(new RecordingCommand(domain, "Edit species") {
			@Override
			protected void doExecute() {
				final var connections = SpeciesWizardAdapter.parentConnections(getDiagram(), species);
				SpeciesWizardAdapter.apply(species, wizard.getResult());
				SpeciesWizardAdapter.syncParentConnection(getFeatureProvider(), getDiagram(), species, connections);
				updatePictogramElement(shape);
				layoutPictogramElement(shape);
			}
		});
		hasDoneChanges = true;
		ModelGenerator.modelValidation(getFeatureProvider(), getDiagram());
	}

	private void editWorld(final EWorldAgent world, final PictogramElement shape) {
		final var diagram = getDiagram();
		final var editor = ExampleUtil.getDiagramEditor(getFeatureProvider());
		final var initial = SpeciesWizardAdapter.modelSnapshot(diagram, world);
		final var documentation = DiagramModelDocumentation.read(diagram);
		final var uri = diagram.eResource().getURI().trimFragment();
		final var container = uri.trimSegments(1);
		final var location = new ModelLocation(
				container.isPlatformResource() ? container.toPlatformString(true) : container.toString(), uri.lastSegment());
		final var wizard = new SpeciesWizard(editor.getSite().getShell(), initial,
				draft -> SpeciesWizardAdapter.validate(getFeatureProvider(), diagram, world, null, draft),
				true, SpeciesWizardAdapter.wizardContext(world), location, false, documentation);
		wizard.setCreationMode(false);
		if (wizard.open() != Window.OK || initial.equals(wizard.getResult())
				&& documentation.equals(wizard.getDocumentation())) return;
		if (!initial.equals(SpeciesWizardAdapter.modelSnapshot(diagram, world))
				|| !documentation.equals(DiagramModelDocumentation.read(diagram))) {
			MessageDialog.openError(editor.getSite().getShell(), "Model changed",
					"The model changed while the wizard was open. Reopen it before applying edits.");
			return;
		}
		final var domain = TransactionUtil.getEditingDomain(world);
		if (domain == null) {
			MessageDialog.openError(editor.getSite().getShell(), "Cannot edit model",
					"The world is not attached to an editable diagram.");
			return;
		}
		domain.getCommandStack().execute(new RecordingCommand(domain, "Edit model") {
			@Override
			protected void doExecute() {
				SpeciesWizardAdapter.applyModel(diagram, world, wizard.getResult());
				DiagramModelDocumentation.write(diagram, wizard.getDocumentation());
				updatePictogramElement(shape);
				layoutPictogramElement(shape);
			}
		});
		hasDoneChanges = true;
		ModelGenerator.modelValidation(getFeatureProvider(), diagram);
	}
}
