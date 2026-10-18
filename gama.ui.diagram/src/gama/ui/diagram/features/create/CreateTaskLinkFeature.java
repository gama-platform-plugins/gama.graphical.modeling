package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import gama.ui.diagram.swt.editFrame.DiagramBehavior;
import gama.ui.diagram.swt.image.GamaImageProvider;

public class CreateTaskLinkFeature extends CreateBehaviorLinkFeature {
	public CreateTaskLinkFeature(final IFeatureProvider fp) {
		super(fp, DiagramBehavior.TASK, GamaImageProvider.IMG_TASK);
	}
}
