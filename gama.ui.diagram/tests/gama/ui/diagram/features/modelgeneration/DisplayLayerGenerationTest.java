package gama.ui.diagram.features.modelgeneration;

import java.io.StringReader;
import java.util.List;

import org.eclipse.xtext.parser.IParser;

import gama.ui.diagram.metamodel.GamaFactory;
import gaml.compiler.GamlStandaloneSetup;

public final class DisplayLayerGenerationTest {
	public static void main(final String[] args) {
		final var parser = new GamlStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
		final var factory = GamaFactory.eINSTANCE;
		final var display = factory.createEDisplay();
		display.setName("Display 'quoted' \"name\"");
		final var relation = factory.createEDisplayLink();
		relation.setDisplay(display);
		for (final String kind : List.of("species", "grid", "agents", "image", "text", "chart")) {
			final var layer = factory.createELayer();
			layer.setType(kind);
			layer.setGamlCode("");
			layer.setName("Layer 'quoted' \"name\"");
			layer.setSpecies("people");
			layer.setGrid("cells");
			layer.setAgents("people");
			layer.setAspect("default");
			if ("grid".equals(kind)) {
				layer.setShowLines(true);
				layer.setIsColorCst(true);
				layer.getColorRBG().addAll(List.of(128, 128, 128));
			}
			layer.setFile("images/a 'quoted' \"file\".png");
			layer.setText("A 'quoted' \"label\" with \\ and\nnewline");
			layer.setChart_type("series");
			if ("chart".equals(kind)) {
				final var data = factory.createEChartLayer();
				data.setName("Data 'quoted' \"label\"");
				data.setStyle("line");
				data.setValue("cycle");
				layer.getChartlayers().add(data);
			}
			display.getLayers().add(layer);
			final String declaration = ModelGenerator.defineLayer(layer);
			if (!declaration.contains("text".equals(kind) ? "graphics" : kind)) {
				throw new AssertionError("Missing layer keyword: " + kind);
			}
			if ("grid".equals(kind) && (!declaration.contains("border:") || declaration.contains(" lines:"))) {
				throw new AssertionError("Legacy grid borders use an unsupported facet");
			}
			final String source = "model display_layers\nexperiment run type:gui { output {"
					+ ModelGenerator.defineDisplay(relation) + "} }";
			final var result = parser.parse(new StringReader(source));
			if (result.hasSyntaxErrors()) {
				throw new AssertionError("Invalid " + kind + " layer: " + result.getSyntaxErrors().iterator().next()
						.getSyntaxErrorMessage().getMessage() + "\n" + source);
			}
		}
		final String structured = ModelGenerator.displayBody(display);
		if (!structured.equals(display.getLayers().stream().map(ModelGenerator::defineLayer)
				.collect(java.util.stream.Collectors.joining()))) {
			throw new AssertionError("Display body changed layer order");
		}
		final String raw = "\n// untouched\nchart \"Raw\" type:series { data \"x\" value:cycle; }\n";
		display.setDefineGamlCode(true);
		display.setGamlCode(raw);
		if (!raw.equals(ModelGenerator.displayBody(display))) throw new AssertionError("Raw display body changed");
		display.setDefineGamlCode(false);
		display.getLayers().clear();
		final List<String> blocks = List.of(
				"\n// mesh\nmesh [[1.0,2.0],[3.0,4.0]];\n",
				"\ngraphics \"drawing\" { draw circle(1) color:#red; draw square(2); }\n",
				"\noverlay \"HUD\" { draw \"Cycle\" at:{0,0}; }\n",
				"\nspecies people { aspect alternate { draw circle(2); } }\n",
				"\nagents \"selected\" value:people { draw circle(3); }\n",
				"\nevent \"mouse_down\" { write \"clicked\"; }\n");
		for (int i = 0; i < blocks.size(); i++) {
			final var layer = factory.createELayer();
			layer.setType(List.of("mesh", "graphics", "overlay", "species", "agents", "raw").get(i));
			layer.setGamlCode(blocks.get(i));
			display.getLayers().add(layer);
			if (!blocks.get(i).equals(ModelGenerator.defineLayer(layer))) {
				throw new AssertionError("Source-backed " + layer.getType() + " body was regenerated");
			}
		}
		display.setTrailingSource("\n// trailing display comment\n");
		if (!(String.join("", blocks) + display.getTrailingSource()).equals(ModelGenerator.displayBody(display))) {
			throw new AssertionError("Source-backed blocks changed order or trailing comments");
		}
		final var parsed = parser.parse(new StringReader("model layer_blocks\nspecies people;\nexperiment run type:gui { output {"
				+ ModelGenerator.defineDisplay(relation) + "} }"));
		if (parsed.hasSyntaxErrors()) throw new AssertionError("Source-backed mixed layer blocks do not parse");
		System.out.println("PASS: six legacy kinds, six source-backed layer blocks, labels, order and retained raw bodies");
	}
}
