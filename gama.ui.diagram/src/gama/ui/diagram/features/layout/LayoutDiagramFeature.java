package gama.ui.diagram.features.layout;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.draw2d.PositionConstants;
import org.eclipse.draw2d.geometry.Insets;
import org.eclipse.draw2d.graph.DirectedGraph;
import org.eclipse.draw2d.graph.DirectedGraphLayout;
import org.eclipse.draw2d.graph.Edge;
import org.eclipse.draw2d.graph.Node;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICustomContext;
import org.eclipse.graphiti.features.custom.AbstractCustomFeature;
import org.eclipse.graphiti.mm.pictograms.AnchorContainer;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.FreeFormConnection;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.graphiti.services.Graphiti;

import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.ESpecies;

public class LayoutDiagramFeature extends AbstractCustomFeature {

	public LayoutDiagramFeature(final IFeatureProvider fp) {
		super(fp);
	}

	@Override
	public String getDescription() {
		return "Fit boxes to their contents and arrange the model in a top-down hierarchy";
	}

	@Override
	public String getName() {
		return "&Layout Diagram";
	}

	@Override
	public boolean canExecute(final ICustomContext context) {
		return getDiagram() != null && !getDiagram().getChildren().isEmpty();
	}

	@Override
	public void execute(final ICustomContext context) {
		execute(getDiagram());
	}

	public static void execute(final Diagram diagram) {
		final DirectedGraph graph = new DirectedGraph();
		graph.setDirection(PositionConstants.SOUTH);
		graph.setDefaultPadding(new Insets(60, 30, 60, 30));
		graph.setMargin(new Insets(30));
		final Map<AnchorContainer, Node> nodes = new HashMap<>();
		for (final Shape shape : diagram.getChildren()) {
			final Object object = Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement(shape);
			if (shape instanceof ContainerShape && object instanceof EGamaObject) {
				LayoutCommonFeature.layout((ContainerShape) shape, diagram, (EGamaObject) object, true);
			}
			final var bounds = shape.getGraphicsAlgorithm();
			if (bounds == null) continue;
			final Node node = new Node(shape);
			node.width = bounds.getWidth();
			node.height = bounds.getHeight();
			graph.nodes.add(node);
			nodes.put(shape, node);
		}
		if (graph.nodes.isEmpty()) return;
		for (final Connection connection : diagram.getConnections()) {
			if (connection.getStart() == null || connection.getEnd() == null) continue;
			final Node source = nodes.get(connection.getStart().getParent());
			final Node target = nodes.get(connection.getEnd().getParent());
			// Self-relations and connections outside this diagram do not constrain node placement.
			if (source == null || target == null || source == target) continue;
			final Edge edge = new Edge(connection, source, target);
			final Object targetObject = Graphiti.getLinkService()
					.getBusinessObjectForLinkedPictogramElement((Shape) target.data);
			final boolean structural = targetObject instanceof ESpecies || targetObject instanceof EExperiment;
			edge.setDelta(structural ? 2 : 1);
			edge.weight = structural ? 4 : 1;
			graph.edges.add(edge);
		}
		new DirectedGraphLayout().visit(graph);
		for (final Node node : graph.nodes) {
			final var bounds = ((Shape) node.data).getGraphicsAlgorithm();
			bounds.setX(node.x);
			bounds.setY(node.y);
		}
		for (final Connection connection : diagram.getConnections()) {
			if (connection instanceof FreeFormConnection) {
				((FreeFormConnection) connection).getBendpoints().clear();
			}
		}
	}
}
