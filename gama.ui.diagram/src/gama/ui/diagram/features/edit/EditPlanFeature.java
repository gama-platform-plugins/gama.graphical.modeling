package gama.ui.diagram.features.edit;

import org.eclipse.graphiti.features.IFeatureProvider;
import gama.ui.diagram.editor.MyGamaToolBehaviorProvider;
import gama.ui.diagram.swt.editFrame.DiagramBehavior;
import gama.ui.diagram.swt.editFrame.EditFrame;

public class EditPlanFeature extends EditBehaviorFeature {
	public EditPlanFeature(final IFeatureProvider fp, final EditFrame frame, final MyGamaToolBehaviorProvider tbp) {
		super(fp, frame, tbp, DiagramBehavior.PLAN);
	}
}
