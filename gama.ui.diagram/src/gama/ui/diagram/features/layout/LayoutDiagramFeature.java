package gama.ui.diagram.features.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICustomContext;
import org.eclipse.graphiti.features.custom.AbstractCustomFeature;
import org.eclipse.graphiti.mm.algorithms.styles.Point;
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
	private static final int HORIZONTAL_GAP = 80;
	private static final int VERTICAL_GAP = 40;

	private record Position(double x, double y) {}
	private record Row(double top, double bottom) {}

	public LayoutDiagramFeature(final IFeatureProvider fp) {
		super(fp);
	}

	@Override
	public String getDescription() {
		return "Fit boxes to their contents and arrange the model like GAMA's dependency graph";
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
		if (graph.vertexSet().size() == 1) {
			model.set(graph.vertexSet().iterator().next(), 0, 0);
		} else {
			algorithm.visit(model);
		}

		final Map<Double, List<Shape>> layers = new TreeMap<>();
		final Map<Double, Double> heights = new TreeMap<>();
		final Map<Connection, List<Position>> articulations = new LinkedHashMap<>();
		for (final Shape shape : graph.vertexSet()) {
			final var position = model.get(shape);
			if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y)) {
				throw new IllegalStateException("Invalid diagram layout position");
			}
			layers.computeIfAbsent(position.y, key -> new ArrayList<>()).add(shape);
			heights.merge(position.y, (double) shape.getGraphicsAlgorithm().getHeight(), Math::max);
		}
		for (final Connection connection : graph.edgeSet()) {
			final var points = algorithm.getEdgeArticulationFunction().apply(connection).stream()
					.map(point -> new Position(point.x, point.y)).toList();
			articulations.put(connection, points);
			for (final var point : points) {
				if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
					throw new IllegalStateException("Invalid diagram connection layout");
				}
				heights.putIfAbsent(point.y(), 0.0);
			}
		}

		// Repack real and routing-only layers rather than using Jungrapht's square viewport.
		final Map<Double, Double> centers = new LinkedHashMap<>();
		final Map<Double, Row> rows = new LinkedHashMap<>();
		double y = MARGIN;
		for (final var layer : heights.entrySet()) {
			final double height = Math.max(12, layer.getValue());
			final double center = y + height / 2;
			centers.put(layer.getKey(), center);
			rows.put(center, new Row(y, y + height));
			y += height + VERTICAL_GAP;
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
		for (final var points : articulations.values()) {
			for (final var point : points) {
				for (final Shape shape : layers.getOrDefault(point.y(), List.of())) {
					final double distance = Math.abs(point.x() - model.get(shape).x);
					if (distance > 1e-8) {
						horizontalScale = Math.max(horizontalScale,
								(shape.getGraphicsAlgorithm().getWidth() / 2.0 + HORIZONTAL_GAP / 2.0) / distance);
					}
				}
			}
		}
		if (horizontalScale == 0) horizontalScale = 1;
		for (final Shape shape : graph.vertexSet()) {
			final var bounds = shape.getGraphicsAlgorithm();
			bounds.setX((int) Math.round(model.get(shape).x * horizontalScale - bounds.getWidth() / 2.0));
			bounds.setY((int) Math.round(centers.get(model.get(shape).y) - bounds.getHeight() / 2.0));
		}

		final Map<Connection, List<Position>> routes = new LinkedHashMap<>();
		for (final Connection connection : diagram.getConnections()) {
			if (!(connection instanceof FreeFormConnection)
					|| connection.getStart() == null || connection.getEnd() == null) continue;
			final Shape source = nodes.get(connection.getStart().getParent());
			final Shape target = nodes.get(connection.getEnd().getParent());
			if (source == null || target == null) continue;
			final List<Position> points;
			if (source == target) {
				points = loop(source);
			} else {
				final var path = new ArrayList<Position>();
				for (final var point : articulations.get(connection)) {
					path.add(new Position(point.x() * horizontalScale, centers.get(point.y())));
				}
				final var start = center(source);
				final var end = center(target);
				if (path.size() < 2) {
					path.clear();
					path.add(start);
					path.add(end);
				}
				if (distance(path.getFirst(), start) > distance(path.getLast(), start)) Collections.reverse(path);
				path.set(0, start);
				path.set(path.size() - 1, end);
				points = route(path, rows, rows.get(centers.get(model.get(source).y)),
						rows.get(centers.get(model.get(target).y)));
			}
			routes.put(connection, points);
		}
		double minX = graph.vertexSet().stream().mapToInt(shape -> shape.getGraphicsAlgorithm().getX()).min().orElse(0);
		for (final var points : routes.values()) {
			for (final var point : points) minX = Math.min(minX, point.x());
		}
		final int offsetX = MARGIN - (int) Math.floor(minX);
		for (final Shape shape : graph.vertexSet()) {
			final var bounds = shape.getGraphicsAlgorithm();
			bounds.setX(bounds.getX() + offsetX);
		}
		for (final var route : routes.entrySet()) {
			applyRoute((FreeFormConnection) route.getKey(), route.getValue(), offsetX);
		}
	}

	/** Reattach a new live relation after the user's saved box positions have been restored. */
	public static void routeConnection(final FreeFormConnection connection) {
		if (connection.getStart() == null || connection.getEnd() == null
				|| !(connection.getStart().getParent() instanceof Shape source)
				|| !(connection.getEnd().getParent() instanceof Shape target)) return;
		final var from = source.getGraphicsAlgorithm();
		final var to = target.getGraphicsAlgorithm();
		final var points = source == target ? loop(source)
				: route(List.of(center(source), center(target)), Map.of(),
						new Row(from.getY(), from.getY() + from.getHeight()),
						new Row(to.getY(), to.getY() + to.getHeight()));
		applyRoute(connection, points, 0);
	}

	private static List<Position> loop(final Shape source) {
		final var bounds = source.getGraphicsAlgorithm();
		final double bottom = bounds.getY() + bounds.getHeight() + VERTICAL_GAP / 2.0;
		final double right = bounds.getX() + bounds.getWidth() + HORIZONTAL_GAP / 2.0;
		return List.of(new Position(center(source).x(), bottom), new Position(right, bottom),
				new Position(right, center(source).y()));
	}

	private static List<Position> route(final List<Position> path, final Map<Double, Row> rows,
			final Row source, final Row target) {
		final var points = new ArrayList<Position>();
		for (int i = 1; i < path.size(); i++) {
			final var previous = path.get(i - 1);
			final var next = path.get(i);
			final var top = rows.getOrDefault(previous.y(), source);
			final var bottom = rows.getOrDefault(next.y(), target);
			final boolean forward = previous.y() <= next.y();
			final boolean overlapping = top.bottom() > bottom.top() && bottom.bottom() > top.top();
			final double channel = previous.y() == next.y() || overlapping
					? Math.max(top.bottom(), bottom.bottom()) + VERTICAL_GAP / 2.0
					: forward ? (top.bottom() + bottom.top()) / 2
					: (top.top() + bottom.bottom()) / 2;
			if (i > 1) points.add(previous);
			points.add(new Position(previous.x(), channel));
			points.add(new Position(next.x(), channel));
		}
		return points;
	}

	private static void applyRoute(final FreeFormConnection connection, final List<Position> points, final int offsetX) {
		final var bends = connection.getBendpoints();
		bends.clear();
		for (final var point : points) {
			final Point bend = Graphiti.getGaService().createPoint((int) Math.round(point.x()) + offsetX,
					(int) Math.round(point.y()));
			if (bends.isEmpty() || bend.getX() != bends.getLast().getX()
					|| bend.getY() != bends.getLast().getY()) bends.add(bend);
		}
	}

	private static double distance(final Position a, final Position b) {
		return Math.hypot(a.x() - b.x(), a.y() - b.y());
	}

	private static Position center(final Shape shape) {
		final var bounds = shape.getGraphicsAlgorithm();
		return new Position(bounds.getX() + bounds.getWidth() / 2, bounds.getY() + bounds.getHeight() / 2);
	}
}
