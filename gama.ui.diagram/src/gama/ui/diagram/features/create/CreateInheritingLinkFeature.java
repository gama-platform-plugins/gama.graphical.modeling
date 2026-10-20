package gama.ui.diagram.features.create;


import gama.ui.diagram.metamodel.EInheritLink;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EWorldAgent;
import gama.ui.diagram.metamodel.EClassSpecies;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.swt.image.GamaImageProvider;

import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICreateConnectionContext;
import org.eclipse.graphiti.features.context.impl.AddConnectionContext;
import org.eclipse.graphiti.mm.pictograms.Connection;

public class CreateInheritingLinkFeature extends AbstractCreateSpeciesComponentLinkFeature {

	  public CreateInheritingLinkFeature(IFeatureProvider fp) {
		  this(fp, false);
	  }

	  private final boolean classes;

	  protected CreateInheritingLinkFeature(final IFeatureProvider fp, final boolean classes) {
          // provide name and description for the UI, e.g. the palette
          super(fp, "Parent of...", classes ? "The source class is parent of the target class"
                  : "The source species is parent of the target species");
          this.classes = classes;
  }

  public Connection create(ICreateConnectionContext context) {
          if (!canCreate(context)) return null;
          Connection newConnection = null;
          ESpecies source = getESpecies(context.getSourceAnchor());
          ESpecies target = getESpecies(context.getTargetAnchor());
          if (source != null && target != null) {
                  // create new business object
                  EInheritLink eReference =  gama.ui.diagram.metamodel.GamaFactory.eINSTANCE.createEInheritLink();
                  //eReference.setModel(source.getModel());
                  getDiagram().eResource().getContents().add(eReference);
                  eReference.setParent(source);
                  eReference.setChild(target);
                  eReference.setSource(source);
                  eReference.setTarget(target);
                  // add connection for business object
                   AddConnectionContext addContext =
                          new AddConnectionContext(context.getSourceAnchor(), context
                              .getTargetAnchor());
                      addContext.setNewObject(eReference);
              newConnection =
                          (Connection) getFeatureProvider().addIfPossible(addContext);
                  target.setInheritsFrom(source);
                  source.getInheritingLinks().add(eReference);
                  target.getFacets().removeIf(facet -> "parent".equals(facet.getName()));
          }

          return newConnection;
  }

  public boolean canCreate(ICreateConnectionContext context) {
          ESpecies source = getESpecies(context.getSourceAnchor());
          ESpecies target = getESpecies(context.getTargetAnchor());
          return eligible(source) && eligible(target) && target.getInheritsFrom() == null
                  && canInherit(source, target);
  }

  public boolean canStartConnection(ICreateConnectionContext context) {
          ESpecies source = getESpecies(context.getSourceAnchor());
          return eligible(source);
  }

  private boolean eligible(final ESpecies species) {
	  return species != null && !(species instanceof EWorldAgent) && !(species instanceof EExperiment)
			  && (species instanceof EClassSpecies) == classes;
  }

  public static boolean canInherit(final ESpecies parent, final ESpecies child) {
	  if (parent == null || child == null || parent instanceof EWorldAgent || child instanceof EWorldAgent
			  || parent instanceof EExperiment || child instanceof EExperiment
			  || (parent instanceof EClassSpecies) != (child instanceof EClassSpecies)) return false;
	  final var visited = new java.util.HashSet<ESpecies>();
	  for (ESpecies current = parent; current != null; current = current.getInheritsFrom()) {
		  if (current == child || !visited.add(current)) return false;
	  }
	  return true;
  }
  

  
  @Override
  public String getCreateImageId() {
          return GamaImageProvider.IMG_INHERITINGLINK;
  }

}
