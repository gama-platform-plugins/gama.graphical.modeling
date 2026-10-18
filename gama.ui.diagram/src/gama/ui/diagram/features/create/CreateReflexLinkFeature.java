package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import gama.ui.diagram.swt.editFrame.DiagramBehavior;
import gama.ui.diagram.swt.image.GamaImageProvider;

public class CreateReflexLinkFeature extends CreateBehaviorLinkFeature {
	public CreateReflexLinkFeature(final IFeatureProvider fp) {
		super(fp, DiagramBehavior.REFLEX, GamaImageProvider.IMG_REFLEXLINK);
	}
}
