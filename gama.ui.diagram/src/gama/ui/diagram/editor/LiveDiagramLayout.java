package gama.ui.diagram.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.graphiti.mm.algorithms.styles.Point;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.FreeFormConnection;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.graphiti.services.Graphiti;

import gama.ui.diagram.features.layout.LayoutCommonFeature;
import gama.ui.diagram.features.modelgeneration.DiagramSourceReference;
import gama.ui.diagram.metamodel.EGamaLink;
import gama.ui.diagram.metamodel.EGamaModel;
import gama.ui.diagram.metamodel.EGamaObject;

/** Presentation state survives a new semantic snapshot without retaining old compiler objects. */
final class LiveDiagramLayout {

	private record Box(int x, int y, int width, int height, List<Integer> color) {}
	private record Edge(String type, List<String> source, List<String> target) {}

	private final Map<List<String>, Box> boxes = new HashMap<>();
	private final Set<List<String>> ambiguous = new HashSet<>();
	private final Map<Edge, List<Point>> bends = new HashMap<>();
	private final Set<List<String>> selection = new HashSet<>();

	LiveDiagramLayout(final Diagram diagram, final PictogramElement[] selected) {
		for (final Shape shape : diagram.getChildren()) {
			final var object = object(shape);
			if (object == null || shape.getGraphicsAlgorithm() == null) continue;
			final var key = DiagramSourceReference.key(object);
			if (key.isEmpty() || ambiguous.contains(key)) continue;
			final var bounds = shape.getGraphicsAlgorithm();
			if (boxes.putIfAbsent(key, new Box(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(),
					List.copyOf(object.getColorPicto()))) != null) {
				boxes.remove(key);
				ambiguous.add(key);
			}
		}
		for (final var connection : diagram.getConnections()) {
			final var businessObject = Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement(connection);
			if (connection instanceof FreeFormConnection free && businessObject instanceof EGamaLink link) {
				final Edge key = edge(link);
				if (boxes.containsKey(key.source()) && boxes.containsKey(key.target())) {
					bends.put(key, new ArrayList<>(EcoreUtil.copyAll(free.getBendpoints())));
				}
			}
		}
		if (selected != null) for (final var element : selected) {
			final var object = object(element);
			if (object != null) selection.add(DiagramSourceReference.key(object));
		}
	}

	void restoreColors(final EGamaModel model) {
		for (final var object : model.getObjects()) {
			final Box box = boxes.get(DiagramSourceReference.key(object));
			if (box != null) {
				object.getColorPicto().clear();
				object.getColorPicto().addAll(box.color());
			}
		}
	}

	PictogramElement[] restore(final Diagram diagram) {
		final List<PictogramElement> selected = new ArrayList<>();
		for (final Shape shape : diagram.getChildren()) {
			final var object = object(shape);
			if (object == null) continue;
			final var key = DiagramSourceReference.key(object);
			final Box box = boxes.get(key);
			if (box != null) {
				final var bounds = shape.getGraphicsAlgorithm();
				bounds.setX(box.x());
				bounds.setY(box.y());
				bounds.setWidth(Math.max(box.width(), bounds.getWidth()));
				bounds.setHeight(Math.max(box.height(), bounds.getHeight()));
				if (shape instanceof org.eclipse.graphiti.mm.pictograms.ContainerShape container) {
					LayoutCommonFeature.layout(container, diagram, object, false);
				}
			}
			if (box != null && selection.contains(key)) selected.add(shape);
		}
		for (final var connection : diagram.getConnections()) {
			final var businessObject = Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement(connection);
			if (connection instanceof FreeFormConnection free && businessObject instanceof EGamaLink link) {
				final var points = bends.get(edge(link));
				if (points != null) free.getBendpoints().addAll(points);
			}
		}
		return selected.toArray(PictogramElement[]::new);
	}

	private static EGamaObject object(final PictogramElement element) {
		final var object = Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement(element);
		return object instanceof EGamaObject gama ? gama : null;
	}

	private static Edge edge(final EGamaLink link) {
		return new Edge(link.eClass().getName(), DiagramSourceReference.key(link.getSource()),
				DiagramSourceReference.key(link.getTarget()));
	}
}
