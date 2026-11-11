package gama.ui.diagram.features.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import org.jgrapht.graph.DirectedPseudograph;
import org.jungrapht.visualization.layout.algorithms.SugiyamaLayoutAlgorithm;
import org.jungrapht.visualization.layout.model.LayoutModel;
import org.jungrapht.visualization.layout.model.Rectangle;

import gama.ui.diagram.metamodel.EGamaObject;

public class LayoutDiagramFeature extends AbstractCustomFeature {

	private static final int MARGIN = 30;
	private static final int HORIZONTAL_GAP = 60;
	private static final int VERTICAL_GAP = 120;

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
		final var graph = new DirectedPseudograph<Shape, Connection>(null, null, false);
		final Map<AnchorContainer, Shape> nodes = new LinkedHashMap<>();
		for (final Shape shape : diagram.getChildren()) {
			final Object object = Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement(shape);
			if (shape instanceof ContainerShape && object instanceof EGamaObject) {
				LayoutCommonFeature.layout((ContainerShape) shape, diagram, (EGamaObject) object, true);
			}
			if (shape.getGraphicsAlgorithm() == null) continue;
			graph.addVertex(shape);
			nodes.put(shape, shape);
		}
		if (graph.vertexSet().isEmpty()) return;
		for (final Connection connection : diagram.getConnections()) {
			if (connection.getStart() == null || connection.getEnd() == null) continue;
			final Shape source = nodes.get(connection.getStart().getParent());
			final Shape target = nodes.get(connection.getEnd().getParent());
			// Self-relations and connections outside this diagram do not constrain node placement.
			if (source == null || target == null || source == target) continue;
			graph.addEdge(source, target, connection);
		}
		final var model = LayoutModel.<Shape>builder().graph(graph).size(1000, 1000).build();
		if (graph.vertexSet().size() == 1) {
			model.set(graph.vertexSet().iterator().next(), 0, 0);
		} else {
			final var algorithm = SugiyamaLayoutAlgorithm.<Shape, Connection>edgeAwareBuilder()
					.threaded(false).separateComponents(false).expandLayout(false).maxLevelCross(8)
					.postStraighten(false)
					.vertexBoundsFunction(shape -> {
						if (shape == null) return Rectangle.IDENTITY;
						final var bounds = shape.getGraphicsAlgorithm();
						return Rectangle.of(-(bounds.getWidth() + HORIZONTAL_GAP) / 2.0,
								-(bounds.getHeight() + VERTICAL_GAP) / 2.0,
								bounds.getWidth() + HORIZONTAL_GAP, bounds.getHeight() + VERTICAL_GAP);
					}).build();
			algorithm.visit(model);
		}

		// Jungrapht scales coordinates to the layout area, so restore spacing for the actual box sizes.
		final Map<Double, List<Shape>> layers = new TreeMap<>();
		for (final Shape shape : graph.vertexSet()) {
			final var position = model.get(shape);
			if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y)) {
				throw new IllegalStateException("Invalid diagram layout position");
			}
			layers.computeIfAbsent(position.y, key -> new ArrayList<>()).add(shape);
		}
		double horizontalScale = 0;
		for (final List<Shape> layer : layers.values()) {
			layer.sort(Comparator.comparingDouble(shape -> model.get(shape).x));
			for (int i = 1; i < layer.size(); i++) {
				final Shape previous = layer.get(i - 1), current = layer.get(i);
				final double distance = model.get(current).x - model.get(previous).x;
				if (distance > 1e-8) {
					final double required = (previous.getGraphicsAlgorithm().getWidth()
							+ current.getGraphicsAlgorithm().getWidth()) / 2.0 + HORIZONTAL_GAP;
					horizontalScale = Math.max(horizontalScale, required / distance);
				}
			}
		}
		if (horizontalScale == 0) horizontalScale = 1;
		int y = MARGIN;
		for (final List<Shape> layer : layers.values()) {
			int height = 0;
			for (final Shape shape : layer) {
				final var bounds = shape.getGraphicsAlgorithm();
				height = Math.max(height, bounds.getHeight());
			}
			int nextX = Integer.MIN_VALUE;
			for (final Shape shape : layer) {
				final var bounds = shape.getGraphicsAlgorithm();
				final int x = (int) Math.round(model.get(shape).x * horizontalScale - bounds.getWidth() / 2.0);
				bounds.setX(Math.max(x, nextX));
				bounds.setY(y + (height - bounds.getHeight()) / 2);
				nextX = bounds.getX() + bounds.getWidth() + HORIZONTAL_GAP;
			}
			y += height + VERTICAL_GAP;
		}
		final int left = graph.vertexSet().stream().mapToInt(shape -> shape.getGraphicsAlgorithm().getX()).min()
				.orElse(0);
		for (final Shape shape : graph.vertexSet()) {
			final var bounds = shape.getGraphicsAlgorithm();
			bounds.setX(bounds.getX() - left + MARGIN);
		}
		for (final Connection connection : diagram.getConnections()) {
			if (connection instanceof FreeFormConnection) {
				((FreeFormConnection) connection).getBendpoints().clear();
			}
		}
	}
}
