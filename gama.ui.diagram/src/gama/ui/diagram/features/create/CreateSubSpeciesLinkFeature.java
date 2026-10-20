package gama.ui.diagram.features.create;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.IContext;
import org.eclipse.graphiti.features.context.ICreateConnectionContext;
import org.eclipse.graphiti.features.context.impl.AddConnectionContext;
import org.eclipse.graphiti.features.context.impl.AddContext;
import org.eclipse.graphiti.features.context.impl.CreateContext;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.jface.window.Window;
import org.eclipse.graphiti.services.Graphiti;

import gama.ui.diagram.editor.GamaDiagramEditor;
import gama.ui.diagram.features.ExampleUtil;
import gama.ui.diagram.features.add.AddSpeciesFeature;
import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.ESubSpeciesLink;
import gama.ui.diagram.swt.image.GamaImageProvider;
import gama.ui.diagram.swt.editFrame.SpeciesWizardAdapter;
import gaml.compiler.ui.wizards.species.SpeciesWizard;

public class CreateSubSpeciesLinkFeature extends AbstractCreateSpeciesComponentLinkFeature {

   
	public CreateSubSpeciesLinkFeature(IFeatureProvider fp) {
		this(fp, false);
	}

	private final boolean classes;

	protected CreateSubSpeciesLinkFeature(final IFeatureProvider fp, final boolean classes) {
		super(fp, classes ? "Add class..." : "Add species...",
				classes ? "Create a class belonging to the world" : "Create a new species");
		this.classes = classes;
	}

	private ESpecies createESpecies(ESpecies source) {
		if (source == null) return null;
		String newSpeciesName = classes ? "my_class" : "my_species";
	    String initName = newSpeciesName;
	    List<String> names = new ArrayList<String>();
	    for (ESubSpeciesLink li : source.getMicroSpeciesLinks()){
	    	if (li.getMicro() == null) continue;
	    	names.add(li.getMicro().getName());
	    }
	    int cpt = 2;
	    while (names.contains(newSpeciesName)) {
	    	newSpeciesName = initName + cpt;
	    	cpt++;
	    }
	    ESpecies newSpecies = classes ? gama.ui.diagram.metamodel.GamaFactory.eINSTANCE.createEClassSpecies()
	            : gama.ui.diagram.metamodel.GamaFactory.eINSTANCE.createESpecies();
	    newSpecies.setError("");
	    newSpecies.setHasError(false);
		newSpecies.setName(newSpeciesName);
		final var editor = ExampleUtil.getDiagramEditor(getFeatureProvider());
		final var wizard = new SpeciesWizard(editor.getSite().getShell(), SpeciesWizardAdapter.snapshot(newSpecies),
				draft -> SpeciesWizardAdapter.validate(getFeatureProvider(), getDiagram(), newSpecies, source, draft),
				true, SpeciesWizardAdapter.parentNames(source, null, classes),
				SpeciesWizardAdapter.wizardContext(source, null, classes),
				classes ? SpeciesWizard.Kind.CLASS : SpeciesWizard.Kind.SPECIES);
		wizard.setCreationMode(true);
		if (wizard.open() != Window.OK) return null;
		
	  	this.getDiagram().eResource().getContents().add(newSpecies);
		SpeciesWizardAdapter.apply(newSpecies, wizard.getResult());
		return newSpecies;
	}
	
	private PictogramElement addESpecies(ICreateConnectionContext context, ESpecies species) {
		CreateContext cc = new CreateContext();
		cc.setLocation(context.getTargetLocation().getX() - (int)(AddSpeciesFeature.INIT_WIDTH/2.0), context.getTargetLocation().getY() - (int)(AddSpeciesFeature.INIT_HEIGHT/2.0));
		cc.setSize(0, 0);
		cc.setTargetContainer(getDiagram());
		final var shape = getFeatureProvider().addIfPossible(new AddContext(cc, species));
		if (shape == null) throw new IllegalStateException("Could not draw the new species or class.");
		final var box = shape.getGraphicsAlgorithm();
		Graphiti.getGaService().setLocation(box, context.getTargetLocation().getX() - box.getWidth() / 2,
				context.getTargetLocation().getY() - box.getHeight() / 2);
		return shape;
	}
	
	public Connection create(ICreateConnectionContext context) {
		if (!canCreate(context)) return null;
		Connection newConnection = null;
		ESpecies source = getESpecies(context.getSourceAnchor());
		ESpecies target = createESpecies(source);
		if (target == null) return null;
		if (source != null && target != null) {
			PictogramElement targetpe = addESpecies(context, target);
			SpeciesWizardAdapter.syncParentConnection(getFeatureProvider(), getDiagram(), target, List.of());
			// create new business object
			ESubSpeciesLink eReference = createEReference(source, target);
			eReference.setMacro(source);
			eReference.setMicro(target);
			eReference.setSource(source);
			eReference.setTarget(target);
			//eReference.setModel(source.getModel());
			getDiagram().eResource().getContents().add(eReference);
			// add connection for business object
			AddConnectionContext addContext = new AddConnectionContext(
					context.getSourceAnchor(), getAnchor(targetpe));
			addContext.setNewObject(eReference);
			newConnection = (Connection) getFeatureProvider().addIfPossible(
					addContext);
			source.getMicroSpeciesLinks().add(eReference);
			target.getMacroSpeciesLinks().add(eReference);
		}
		GamaDiagramEditor diagramEditor = ((GamaDiagramEditor)ExampleUtil.getDiagramEditor(getFeatureProvider()));
		
		diagramEditor.addEOject(target);
		ModelGenerator.modelValidation(getFeatureProvider(), getDiagram());
		
		return newConnection;
	}


	/**
	 * Creates a EReference between two EClasses.
	 */
	private ESubSpeciesLink createEReference(ESpecies source, ESpecies target) {
		ESubSpeciesLink eReference = gama.ui.diagram.metamodel.GamaFactory.eINSTANCE.createESubSpeciesLink();
		return eReference;
	}

	@Override
	public String getCreateImageId() {
		return classes ? GamaImageProvider.IMG_CREATE_CLASS : GamaImageProvider.IMG_SUBSPECIESLINK;
	}
	
	public void execute(IContext context) {
		super.execute(context);
		//ModelGenerator.modelValidation(getFeatureProvider(), getDiagram());
	}
	
	

}
