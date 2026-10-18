package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import gama.ui.diagram.swt.editFrame.DiagramBehavior;
import gama.ui.diagram.swt.image.GamaImageProvider;

public class CreateStateLinkFeature extends CreateBehaviorLinkFeature {
	public CreateStateLinkFeature(final IFeatureProvider fp) {
		super(fp, DiagramBehavior.STATE, GamaImageProvider.IMG_STATE);
	}
}
