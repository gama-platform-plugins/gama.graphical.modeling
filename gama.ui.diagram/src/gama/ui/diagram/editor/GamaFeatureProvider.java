/*******************************************************************************************************
 *
 * GamaFeatureProvider.java, in gama.ui.diagram, is part of the source code of the GAMA modeling and
 * simulation platform (v.1.9.3).
 *
 * (c) 2007-2024 UMI 209 UMMISCO IRD/SU & Partners (IRIT, MIAT, TLU, CTU)
 *
 * Visit https://github.com/gama-platform/gama for license information and contacts.
 *
 ********************************************************************************************************/
package gama.ui.diagram.editor;

import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.graphiti.dt.IDiagramTypeProvider;
import org.eclipse.graphiti.features.IAddFeature;
import org.eclipse.graphiti.features.ICreateConnectionFeature;
import org.eclipse.graphiti.features.ICreateFeature;
import org.eclipse.graphiti.features.IDeleteFeature;
import org.eclipse.graphiti.features.IFeature;
import org.eclipse.graphiti.features.ILayoutFeature;
import org.eclipse.graphiti.features.IRemoveFeature;
import org.eclipse.graphiti.features.IUpdateFeature;
import org.eclipse.graphiti.features.context.IAddContext;
import org.eclipse.graphiti.features.context.ICustomContext;
import org.eclipse.graphiti.features.context.IDeleteContext;
import org.eclipse.graphiti.features.context.ILayoutContext;
import org.eclipse.graphiti.features.context.IPictogramElementContext;
import org.eclipse.graphiti.features.context.IRemoveContext;
import org.eclipse.graphiti.features.context.IUpdateContext;
import org.eclipse.graphiti.features.context.impl.AddConnectionContext;
import org.eclipse.graphiti.features.context.impl.AddContext;
import org.eclipse.graphiti.features.context.impl.CreateConnectionContext;
import org.eclipse.graphiti.features.context.impl.CreateContext;
import org.eclipse.graphiti.features.custom.ICustomFeature;
import org.eclipse.graphiti.mm.pictograms.AnchorContainer;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.graphiti.ui.features.DefaultFeatureProvider;

import gama.ui.diagram.features.add.AddActionFeature;
import gama.ui.diagram.features.add.AddActionLinkFeature;
import gama.ui.diagram.features.add.AddAspectFeature;
import gama.ui.diagram.features.add.AddAspectLinkFeature;
import gama.ui.diagram.features.add.AddBatchExperimentFeature;
import gama.ui.diagram.features.add.AddDisplayFeature;
import gama.ui.diagram.features.add.AddDisplayLinkFeature;
import gama.ui.diagram.features.add.AddEExperimentLinkFeature;
import gama.ui.diagram.features.add.AddEquationFeature;
import gama.ui.diagram.features.add.AddEquationLinkFeature;
import gama.ui.diagram.features.add.AddGridFeature;
import gama.ui.diagram.features.add.AddGuiExperimentFeature;
import gama.ui.diagram.features.add.AddInheritingLinkFeature;
import gama.ui.diagram.features.add.AddPerceiveFeature;
import gama.ui.diagram.features.add.AddPerceiveLinkFeature;
import gama.ui.diagram.features.add.AddPlanFeature;
import gama.ui.diagram.features.add.AddPlanLinkFeature;
import gama.ui.diagram.features.add.AddReflexFeature;
import gama.ui.diagram.features.add.AddReflexLinkFeature;
import gama.ui.diagram.features.add.AddRuleFeature;
import gama.ui.diagram.features.add.AddRuleLinkFeature;
import gama.ui.diagram.features.add.AddSpeciesFeature;
import gama.ui.diagram.features.add.AddStateFeature;
import gama.ui.diagram.features.add.AddStateLinkFeature;
import gama.ui.diagram.features.add.AddSubSpecieLinkFeature;
import gama.ui.diagram.features.add.AddTaskFeature;
import gama.ui.diagram.features.add.AddTaskLinkFeature;
import gama.ui.diagram.features.add.AddWorldFeature;
import gama.ui.diagram.features.create.CreateActionLinkFeature;
import gama.ui.diagram.features.create.CreateAspectLinkFeature;
import gama.ui.diagram.features.create.CreateBatchExperimentLinkFeature;
import gama.ui.diagram.features.create.CreateDisplayLinkFeature;
import gama.ui.diagram.features.create.CreateEquationLinkFeature;
import gama.ui.diagram.features.create.CreateGuiExperimentLinkFeature;
import gama.ui.diagram.features.create.CreateInheritingLinkFeature;
import gama.ui.diagram.features.create.CreatePerceiveLinkFeature;
import gama.ui.diagram.features.create.CreatePlanLinkFeature;
import gama.ui.diagram.features.create.CreateReflexLinkFeature;
import gama.ui.diagram.features.create.CreateRuleLinkFeature;
import gama.ui.diagram.features.create.CreateStateLinkFeature;
import gama.ui.diagram.features.create.CreateSubGridLinkFeature;
import gama.ui.diagram.features.create.CreateSubSpeciesLinkFeature;
import gama.ui.diagram.features.create.CreateTaskLinkFeature;
import gama.ui.diagram.features.layout.LayoutCommonFeature;
import gama.ui.diagram.features.layout.LayoutDiagramFeature;
import gama.ui.diagram.features.layout.LayoutEExperimentFeature;
import gama.ui.diagram.features.layout.LayoutESpeciesFeature;
import gama.ui.diagram.features.modelgeneration.ModelGenerationFeature;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.features.others.ChangeColorEGamaObjectFeature;
import gama.ui.diagram.features.others.CustomDeleteFeature;
import gama.ui.diagram.features.others.EmptyRemoveFeature;
import gama.ui.diagram.features.others.UpdateEGamaObjectFeature;
import gama.ui.diagram.metamodel.EAction;
import gama.ui.diagram.metamodel.EActionLink;
import gama.ui.diagram.metamodel.EAspect;
import gama.ui.diagram.metamodel.EAspectLink;
import gama.ui.diagram.metamodel.EBatchExperiment;
import gama.ui.diagram.metamodel.EDisplay;
import gama.ui.diagram.metamodel.EDisplayLink;
import gama.ui.diagram.metamodel.EEquation;
import gama.ui.diagram.metamodel.EEquationLink;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EExperimentLink;
import gama.ui.diagram.metamodel.EGUIExperiment;
import gama.ui.diagram.metamodel.EGamaLink;
import gama.ui.diagram.metamodel.EGamaModel;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.EGrid;
import gama.ui.diagram.metamodel.EInheritLink;
import gama.ui.diagram.metamodel.EMonitor;
import gama.ui.diagram.metamodel.EParameter;
import gama.ui.diagram.metamodel.EPerceive;
import gama.ui.diagram.metamodel.EPerceiveLink;
import gama.ui.diagram.metamodel.EPlan;
import gama.ui.diagram.metamodel.EPlanLink;
import gama.ui.diagram.metamodel.EReflex;
import gama.ui.diagram.metamodel.EReflexLink;
import gama.ui.diagram.metamodel.ERule;
import gama.ui.diagram.metamodel.ERuleLink;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EState;
import gama.ui.diagram.metamodel.EStateLink;
import gama.ui.diagram.metamodel.ESubSpeciesLink;
import gama.ui.diagram.metamodel.ETask;
import gama.ui.diagram.metamodel.ETaskLink;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.metamodel.GamaFactory;

public class GamaFeatureProvider extends DefaultFeatureProvider {

	private String typeOfModel;
	private EGamaModel importedModel;

	public GamaFeatureProvider(final IDiagramTypeProvider dtp) {
		super(dtp);
	}

	@Override
	public IAddFeature getAddFeature(final IAddContext context) {
		if (context.getNewObject() instanceof EWorldAgent) return new AddWorldFeature(this);
		if (context.getNewObject() instanceof EAction) return new AddActionFeature(this);
		if (context.getNewObject() instanceof EReflex) return new AddReflexFeature(this);
		if (context.getNewObject() instanceof EPlan) return new AddPlanFeature(this);
		if (context.getNewObject() instanceof ETask) return new AddTaskFeature(this);
		if (context.getNewObject() instanceof EState) return new AddStateFeature(this);
		if (context.getNewObject() instanceof ERule) return new AddRuleFeature(this);
		if (context.getNewObject() instanceof EEquation) return new AddEquationFeature(this);
		if (context.getNewObject() instanceof EPerceive) return new AddPerceiveFeature(this);
		if (context.getNewObject() instanceof EAspect) return new AddAspectFeature(this);
		if (context.getNewObject() instanceof EGUIExperiment) return new AddGuiExperimentFeature(this);
		if (context.getNewObject() instanceof EBatchExperiment) return new AddBatchExperimentFeature(this);
		if (context.getNewObject() instanceof EDisplay) return new AddDisplayFeature(this);
		if (context.getNewObject() instanceof ESubSpeciesLink) return new AddSubSpecieLinkFeature(this);
		if (context.getNewObject() instanceof EActionLink) return new AddActionLinkFeature(this);
		if (context.getNewObject() instanceof EReflexLink) return new AddReflexLinkFeature(this);
		if (context.getNewObject() instanceof EEquationLink) return new AddEquationLinkFeature(this);
		if (context.getNewObject() instanceof EPlanLink) return new AddPlanLinkFeature(this);
		if (context.getNewObject() instanceof EStateLink) return new AddStateLinkFeature(this);
		if (context.getNewObject() instanceof ETaskLink) return new AddTaskLinkFeature(this);
		if (context.getNewObject() instanceof ERuleLink) return new AddRuleLinkFeature(this);
		if (context.getNewObject() instanceof EPerceiveLink) return new AddPerceiveLinkFeature(this);
		if (context.getNewObject() instanceof EAspectLink) return new AddAspectLinkFeature(this);
		if (context.getNewObject() instanceof EDisplayLink) return new AddDisplayLinkFeature(this);
		if (context.getNewObject() instanceof EExperimentLink) return new AddEExperimentLinkFeature(this);
		if (context.getNewObject() instanceof EInheritLink) return new AddInheritingLinkFeature(this);
		if (context.getNewObject() instanceof EGrid) return new AddGridFeature(this);
		if (context.getNewObject() instanceof ESpecies) return new AddSpeciesFeature(this);
		return super.getAddFeature(context);
	}

	@Override
	public ICreateFeature[] getCreateFeatures() {
		return new ICreateFeature[] {};
	}

	public void init() {
		final Diagram diagram = getDiagramTypeProvider().getDiagram();
		if (!diagram.getChildren().isEmpty()) return;
		if ("custom".equals(typeOfModel) && importedModel == null) {
			throw new IllegalStateException("No description-based model was supplied to the diagram importer");
		}
		final TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(diagram);
		domain.getCommandStack().execute(new RecordingCommand(domain) {
			@Override
			protected void doExecute() {
				final GamaDiagramEditor editor =
						(GamaDiagramEditor) getDiagramTypeProvider().getDiagramBehavior().getDiagramContainer();
				if ("custom".equals(typeOfModel)) {
					addImportedModel(diagram, editor);
					LayoutDiagramFeature.execute(diagram);
				} else {
					final EWorldAgent world = GamaFactory.eINSTANCE.createEWorldAgent();
					world.setName("world");
					diagram.eResource().getContents().add(world);
					addNode(diagram, editor, world, 100, 50);
					if ("skeleton".equals(typeOfModel)) {
						initSkeleton(world, diagram);
					} else {
						initSimple(world, diagram);
					}
				}
				ModelGenerator.modelValidation(GamaFeatureProvider.this, diagram);
				editor.updateEObjectErrors();
			}
		});
	}

	private void addImportedModel(final Diagram diagram, final GamaDiagramEditor editor) {
		diagram.eResource().getContents().add(importedModel);
		for (final EGamaObject object : importedModel.getObjects()) {
			if (object instanceof EParameter || object instanceof EMonitor) continue;
			addNode(diagram, editor, object, 0, 0);
		}
		for (final EGamaLink link : importedModel.getLinks()) {
			final AnchorContainer source = (AnchorContainer) getPictogramElementForBusinessObject(link.getSource());
			final AnchorContainer target = (AnchorContainer) getPictogramElementForBusinessObject(link.getTarget());
			final AddConnectionContext context = new AddConnectionContext(
					Graphiti.getPeService().getChopboxAnchor(source), Graphiti.getPeService().getChopboxAnchor(target));
			context.setNewObject(link);
			if (addIfPossible(context) == null) {
				throw new IllegalStateException("Cannot draw imported relation " + link.eClass().getName());
			}
		}
	}

	private void addNode(final Diagram diagram, final GamaDiagramEditor editor, final EGamaObject object,
			final int x, final int y) {
		final CreateContext context = new CreateContext();
		context.setTargetContainer(diagram);
		context.setLocation(x, y);
		context.setSize(0, 0);
		editor.addEOject(object);
		if (addIfPossible(new AddContext(context, object)) == null) {
			throw new IllegalStateException("Cannot draw imported element " + object.eClass().getName());
		}
	}

	public void initSimple(final EWorldAgent world, final Diagram diagram) {
		final CreateGuiExperimentLinkFeature create = new CreateGuiExperimentLinkFeature(this);
		final PictogramElement worldShape = getPictogramElementForBusinessObject(world);
		final CreateConnectionContext context = new CreateConnectionContext();
		context.setSourcePictogramElement(worldShape);
		context.setSourceAnchor(Graphiti.getPeService().getChopboxAnchor((AnchorContainer) worldShape));
		context.setTargetLocation(new org.eclipse.graphiti.internal.datatypes.impl.LocationImpl(600, 50));
		create.create(context, false);
	}

	public void initSkeleton(final EWorldAgent world, final Diagram diagram) {
		initSimple(world, diagram);
		for (final org.eclipse.graphiti.mm.pictograms.Shape shape : diagram.getChildren()) {
			if (getBusinessObjectForPictogramElement(shape) instanceof EExperiment) {
				final CreateConnectionContext context = new CreateConnectionContext();
				context.setSourcePictogramElement(shape);
				context.setSourceAnchor(Graphiti.getPeService().getChopboxAnchor((AnchorContainer) shape));
				context.setTargetLocation(new org.eclipse.graphiti.internal.datatypes.impl.LocationImpl(800, 50));
				new CreateDisplayLinkFeature(this).create(context, false);
				break;
			}
		}
	}

	@Override
	public ILayoutFeature getLayoutFeature(final ILayoutContext context) {
		final Object object = getBusinessObjectForPictogramElement(context.getPictogramElement());
		if (object instanceof ESpecies) return new LayoutESpeciesFeature(this);
		if (object instanceof EExperiment) return new LayoutEExperimentFeature(this);
		if (object instanceof EGamaObject) return new LayoutCommonFeature(this);
		return super.getLayoutFeature(context);
	}

	@Override
	public IFeature[] getDragAndDropFeatures(final IPictogramElementContext context) {
		return getCreateConnectionFeatures();
	}

	@Override
	public ICreateConnectionFeature[] getCreateConnectionFeatures() {
		return new ICreateConnectionFeature[] { new CreateSubSpeciesLinkFeature(this),
				new CreateSubGridLinkFeature(this), new CreateInheritingLinkFeature(this),
				new CreateActionLinkFeature(this), new CreateReflexLinkFeature(this), new CreateAspectLinkFeature(this),
				new CreateDisplayLinkFeature(this), new CreateBatchExperimentLinkFeature(this),
				new CreateGuiExperimentLinkFeature(this), new CreatePlanLinkFeature(this),
				new CreateTaskLinkFeature(this), new CreateStateLinkFeature(this), new CreateRuleLinkFeature(this),
				new CreateEquationLinkFeature(this), new CreatePerceiveLinkFeature(this) };
	}

	@Override
	public ICustomFeature[] getCustomFeatures(final ICustomContext context) {
		return new ICustomFeature[] { new ModelGenerationFeature(this), new LayoutDiagramFeature(this),
				new ChangeColorEGamaObjectFeature(this) };
	}

	@Override
	public IRemoveFeature getRemoveFeature(final IRemoveContext context) {
		return new EmptyRemoveFeature(this);
	}

	@Override
	public IDeleteFeature getDeleteFeature(final IDeleteContext context) {
		return new CustomDeleteFeature(this);
	}

	@Override
	public IUpdateFeature getUpdateFeature(final IUpdateContext context) {
		if (context.getPictogramElement() instanceof ContainerShape
				&& getBusinessObjectForPictogramElement(context.getPictogramElement()) instanceof EGamaObject) {
			return new UpdateEGamaObjectFeature(this);
		}
		return super.getUpdateFeature(context);
	}

	public String getTypeOfModel() { return typeOfModel; }

	public void setTypeOfModel(final String typeOfModel) { this.typeOfModel = typeOfModel; }

	public void setImportedModel(final EGamaModel model) { importedModel = model; }
}
