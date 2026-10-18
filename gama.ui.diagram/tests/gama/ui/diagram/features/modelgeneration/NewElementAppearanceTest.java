package gama.ui.diagram.features.modelgeneration;

import java.lang.reflect.Proxy;
import java.util.List;

import org.eclipse.graphiti.dt.IDiagramTypeProvider;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.impl.AddContext;
import org.eclipse.graphiti.mm.pictograms.PictogramsFactory;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.swt.widgets.Display;

import gama.ui.diagram.editor.GamaFeatureProvider;
import gama.ui.diagram.features.add.ColorDisplay;
import gama.ui.diagram.features.add.RelationStyle;
import gama.ui.diagram.features.layout.LayoutCommonFeature;
import gama.ui.diagram.metamodel.GamaFactory;

/** Exercises production add features, rather than just the content-size calculation. */
public final class NewElementAppearanceTest {
	public static void main(final String[] args) {
		final var display = new Display();
		try {
			final var factory = GamaFactory.eINSTANCE;
			final var diagram = Graphiti.getPeCreateService().createDiagram("gama", "appearance", true);
			final var resource = new org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl();
			resource.getContents().add(diagram);
			final IFeatureProvider[] provider = new IFeatureProvider[1];
			final var type = (IDiagramTypeProvider) Proxy.newProxyInstance(IDiagramTypeProvider.class.getClassLoader(),
					new Class<?>[] { IDiagramTypeProvider.class }, (proxy, method, arguments) -> switch (method.getName()) {
						case "getFeatureProvider" -> provider[0];
						case "getDiagram" -> diagram;
						default -> throw new UnsupportedOperationException(method.getName());
					});
			final var fp = new GamaFeatureProvider(type);
			provider[0] = fp;
			final var declaration = factory.createEClassSpecies();
			final var testExperiment = factory.createEGUIExperiment();
			ExperimentKind.set(testExperiment, "test");
			final var legacyGuiExperiment = factory.createEBatchExperiment();
			ExperimentKind.set(legacyGuiExperiment, "gui");
			final var init = factory.createEReflex();
			init.setKind("init");
			final var abort = factory.createEReflex();
			abort.setKind("abort");
			for (final var object : List.of(declaration, factory.createESpecies(), factory.createEGrid(),
					factory.createEWorldAgent(), factory.createEAction(), factory.createEReflex(),
					factory.createEAspect(), factory.createEEquation(), factory.createEPerceive(),
					factory.createEPlan(), factory.createERule(), factory.createEState(), factory.createETask(),
					factory.createEDisplay(), factory.createEGUIExperiment(), factory.createEBatchExperiment(),
					testExperiment, legacyGuiExperiment, init, abort)) {
				object.setName("short");
				final var context = new AddContext();
				context.setNewObject(object);
				context.setTargetContainer(diagram);
				context.setLocation(60, 80);
				final var shape = (org.eclipse.graphiti.mm.pictograms.ContainerShape) fp.getAddFeature(context).add(context);
				final var box = shape.getGraphicsAlgorithm();
				if (shape.getChildren().stream().noneMatch(child ->
						child.getGraphicsAlgorithm() instanceof org.eclipse.graphiti.mm.algorithms.Image icon
						&& gama.ui.diagram.swt.image.GamaImageProvider.nodeImageId(object).equals(icon.getId()))) {
					throw new AssertionError("New element has the wrong semantic icon: " + object.eClass().getName());
				}
				if (box.getHeight() != 50) throw new AssertionError(object.eClass().getName() + " retains oversized default: " + box.getHeight());
				final int width = box.getWidth();
				if (LayoutCommonFeature.layout(shape, diagram, object, true) || box.getWidth() != width) {
					throw new AssertionError("New element is not content-sized: " + object.eClass().getName());
				}
				Graphiti.getGaService().setSize(box, 500, 300);
				LayoutCommonFeature.layout(shape, diagram, object, false);
				if (box.getWidth() != 500 || box.getHeight() != 300) throw new AssertionError("Ordinary layout shrank manual size");
				context.setSize(420, 240);
				final var sized = fp.getAddFeature(context).add(context).getGraphicsAlgorithm();
				if (sized.getWidth() < 420 || sized.getHeight() < 240) throw new AssertionError("Explicit creation size ignored");
			}
			final var ownership = factory.createESubSpeciesLink();
			final var variable = factory.createEVariable();
			variable.setName("an_attribute_with_a_very_long_name");
			variable.setType("float");
			declaration.getVariables().add(variable);
			final var detailed = new AddContext();
			detailed.setTargetContainer(diagram);
			detailed.setNewObject(declaration);
			final var detailedShape = (org.eclipse.graphiti.mm.pictograms.ContainerShape) fp.getAddFeature(detailed).add(detailed);
			if (detailedShape.getGraphicsAlgorithm().getWidth() <= 180
					|| detailedShape.getGraphicsAlgorithm().getHeight() <= 50
					|| LayoutCommonFeature.layout(detailedShape, diagram, declaration, true)) {
				throw new AssertionError("New class does not fit its actual attributes");
			}
			ownership.setMicro(declaration);
			final var connection = Graphiti.getPeCreateService().createFreeFormConnection(diagram);
			Graphiti.getGaService().createPolyline(connection);
			final var link = PictogramsFactory.eINSTANCE.createPictogramLink();
			link.getBusinessObjects().add(ownership);
			connection.setLink(link);
			diagram.getPictogramLinks().add(link);
			final var decorator = Graphiti.getPeCreateService().createConnectionDecorator(connection, true, 0.5, true);
			final var text = Graphiti.getGaService().createDefaultMultiText(diagram, decorator, "has class");
			final var expected = ColorDisplay.nodeTextColor(ColorDisplay.NODE_CLASS);
			RelationStyle.apply(diagram, connection);
			final var actual = connection.getGraphicsAlgorithm().getForeground();
			if (actual.getRed() != expected.getRed() || actual.getGreen() != expected.getGreen()
					|| actual.getBlue() != expected.getBlue()) throw new AssertionError("Class ownership is not class-colored");
			if (text.getForeground() != actual) throw new AssertionError("Class ownership label color differs from line");
			final var speciesOwnership = factory.createESubSpeciesLink();
			speciesOwnership.setMicro(factory.createESpecies());
			link.getBusinessObjects().set(0, speciesOwnership);
			if (RelationStyle.color(connection).getRed() != 143) throw new AssertionError("Species relation color changed");
			link.getBusinessObjects().set(0, ownership);
			declaration.getColorPicto().clear();
			declaration.getColorPicto().addAll(List.of(228, 240, 240));
			if (RelationStyle.isCurrent(connection)) throw new AssertionError("Class color change not detected");
			RelationStyle.apply(diagram, connection);
			if (!RelationStyle.isCurrent(connection)) throw new AssertionError("Class relation color update failed");
			final var lifecycle = factory.createEReflexLink();
			lifecycle.setReflex(init);
			link.getBusinessObjects().set(0, lifecycle);
			RelationStyle.apply(diagram, connection);
			if (!"has the init".equals(text.getValue())) throw new AssertionError("Init relationship mislabeled");
			init.setKind("abort");
			if (RelationStyle.isCurrent(connection)) throw new AssertionError("Lifecycle kind change not detected");
			RelationStyle.apply(diagram, connection);
			if (!"has the abort".equals(text.getValue()) || !RelationStyle.isCurrent(connection)) {
				throw new AssertionError("Lifecycle relationship label not updated");
			}
			System.out.println("PASS: 20 content-sized nodes, semantic icons, manual/explicit sizing, relation colors and lifecycle labels");
		} finally {
			display.dispose();
		}
	}
}
