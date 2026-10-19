package gama.ui.diagram.features.modelgeneration;

import java.util.List;
import java.util.jar.JarFile;

import org.eclipse.swt.graphics.ImageLoader;

import gama.ui.diagram.metamodel.GamaFactory;
import gama.ui.diagram.swt.image.GamaImageProvider;

/** Pass the resolved gama.ui.shared binary jar to check shared SVG decoding as well as node mappings. */
public final class OutlineIconTest {
	public static void main(final String[] args) throws Exception {
		final var factory = GamaFactory.eINSTANCE;
		final var nodes = List.of(factory.createEWorldAgent(), factory.createESpecies(), factory.createEGrid(),
				factory.createEGUIExperiment(), factory.createEBatchExperiment(), factory.createEAction(),
				factory.createEAspect(), factory.createEReflex(), factory.createEDisplay(), factory.createEEquation(),
				factory.createEPerceive(), factory.createEState(), factory.createETask(), factory.createEPlan(),
				factory.createERule(), factory.createEClassSpecies());
		final var expected = List.of(GamaImageProvider.IMG_WORLD, GamaImageProvider.IMG_SUBSPECIESLINK,
				GamaImageProvider.IMG_SUBGRIDLINK, GamaImageProvider.IMG_GUIXPLINK, GamaImageProvider.IMG_BATCHXPLINK,
				GamaImageProvider.IMG_ACTIONLINK, GamaImageProvider.IMG_ASPECTLINK, GamaImageProvider.IMG_REFLEXLINK,
				GamaImageProvider.IMG_DISPLAYLINK, GamaImageProvider.IMG_EQUATION, GamaImageProvider.IMG_PERCEIPT,
				GamaImageProvider.IMG_STATE, GamaImageProvider.IMG_TASK, GamaImageProvider.IMG_PLAN, GamaImageProvider.IMG_RULE,
				GamaImageProvider.IMG_CLASS);
		for (int i = 0; i < nodes.size(); i++) {
			if (!(expected.get(i) + ".dark").equals(GamaImageProvider.nodeImageId(nodes.get(i)))) {
				throw new AssertionError("Incorrect icon for " + nodes.get(i).eClass().getName());
			}
			final var changedKind = factory.createEBatchExperiment();
			ExperimentKind.set(changedKind, "test");
			if (!(GamaImageProvider.IMG_TESTXPLINK + ".dark").equals(GamaImageProvider.nodeImageId(changedKind))) {
				throw new AssertionError("Test experiment keeps legacy batch icon");
			}
			ExperimentKind.set(changedKind, "gui");
			if (!(GamaImageProvider.IMG_GUIXPLINK + ".dark").equals(GamaImageProvider.nodeImageId(changedKind))) {
				throw new AssertionError("GUI experiment keeps legacy batch icon");
			}
		}
		try (final var jar = new JarFile(args[0])) {
			for (final String theme : List.of("light", "dark")) {
				for (final String name : List.of("model", "species", "grid", "gui", "batch", "action", "aspect",
						"reflex", "init", "abort", "display", "equation", "perceive", "state", "task", "rule", "unknown", "class", "test")) {
					final String path = "icons_svg/" + theme + "/gaml/_" + name + ".svg";
					final var entry = jar.getJarEntry(path);
					if (entry == null) throw new AssertionError("Missing outline asset: " + path);
					try (final var stream = jar.getInputStream(entry)) {
						final var images = new ImageLoader().load(stream);
						if (images.length != 1 || images[0].width <= 0 || images[0].height <= 0) {
							throw new AssertionError("Cannot decode outline SVG: " + path);
						}
					}
				}
			}
		}
		System.out.println("PASS: 18 semantic node mappings, 38 shared light/dark outline SVGs");
	}
}
