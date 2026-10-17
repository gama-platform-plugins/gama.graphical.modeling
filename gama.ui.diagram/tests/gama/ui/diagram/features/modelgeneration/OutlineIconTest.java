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
				factory.createERule());
		final var expected = List.of(GamaImageProvider.IMG_WORLD, GamaImageProvider.IMG_SUBSPECIESLINK,
				GamaImageProvider.IMG_SUBGRIDLINK, GamaImageProvider.IMG_GUIXPLINK, GamaImageProvider.IMG_BATCHXPLINK,
				GamaImageProvider.IMG_ACTIONLINK, GamaImageProvider.IMG_ASPECTLINK, GamaImageProvider.IMG_REFLEXLINK,
				GamaImageProvider.IMG_DISPLAYLINK, GamaImageProvider.IMG_EQUATION, GamaImageProvider.IMG_PERCEIPT,
				GamaImageProvider.IMG_STATE, GamaImageProvider.IMG_TASK, GamaImageProvider.IMG_PLAN, GamaImageProvider.IMG_RULE);
		for (int i = 0; i < nodes.size(); i++) {
			if (!(expected.get(i) + ".dark").equals(GamaImageProvider.nodeImageId(nodes.get(i)))) {
				throw new AssertionError("Incorrect icon for " + nodes.get(i).eClass().getName());
			}
		}
		try (final var jar = new JarFile(args[0])) {
			final var folder = jar.getJarEntry("icons_svg/light/navigator/files/folder.model.svg");
			if (folder == null) throw new AssertionError("Missing shared palette folder");
			try (final var stream = jar.getInputStream(folder)) {
				final var images = new ImageLoader().load(stream);
				if (images.length != 1 || images[0].width <= 0 || images[0].height <= 0) {
					throw new AssertionError("Cannot decode palette folder SVG");
				}
			}
			for (final String theme : List.of("light", "dark")) {
				for (final String name : List.of("model", "species", "grid", "gui", "batch", "action", "aspect",
						"reflex", "display", "equation", "perceive", "state", "task", "rule", "unknown")) {
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
		System.out.println("PASS: 15 semantic node mappings, 30 shared light/dark outline SVGs and palette folder SVG");
	}
}
