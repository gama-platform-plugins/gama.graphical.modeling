package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import gama.ui.diagram.swt.editFrame.DiagramBehavior;
import gama.ui.diagram.swt.image.GamaImageProvider;

public class CreatePlanLinkFeature extends CreateBehaviorLinkFeature {
	public CreatePlanLinkFeature(final IFeatureProvider fp) {
		super(fp, DiagramBehavior.PLAN, GamaImageProvider.IMG_PLAN);
	}
}
