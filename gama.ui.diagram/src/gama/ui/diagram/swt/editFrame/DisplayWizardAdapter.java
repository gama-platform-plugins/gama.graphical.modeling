package gama.ui.diagram.swt.editFrame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.xtext.parser.IParser;
import org.eclipse.xtext.resource.IResourceServiceProvider;

import gama.ui.diagram.features.modelgeneration.ModelGenerator;
import gama.ui.diagram.metamodel.EDisplay;
import gama.ui.diagram.metamodel.EDisplayLink;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.ELayer;
import gama.ui.diagram.metamodel.GamaFactory;
import gaml.compiler.ui.wizards.action.ActionBodyContext;
import gaml.compiler.ui.wizards.display.DisplayDraft;
import gaml.compiler.ui.wizards.display.DisplaySource;
import gaml.compiler.ui.wizards.display.LayerDraft;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Facet;

public final class DisplayWizardAdapter {
	private DisplayWizardAdapter() {}

	private static String text(final String value) { return value == null ? "" : value; }

	private static IParser parser() {
		final var provider = IResourceServiceProvider.Registry.INSTANCE
				.getResourceServiceProvider(URI.createURI("memory:/display-wizard.gaml"));
		if (provider == null) throw new IllegalStateException("The GAML parser is not registered.");
		return provider.get(IParser.class);
	}

	private static LayerDraft identified(final LayerDraft layer, final int index) {
		return new LayerDraft("layer:" + index, layer.kind(), layer.expression(), layer.facets(), layer.textFacets(),
				layer.source(), layer.body());
	}

	public static DisplayDraft snapshot(final EDisplay display) {
		final var parser = parser();
		final List<LayerDraft> layers = new ArrayList<>();
		final String trailing;
		if (display.isDefineGamlCode()) {
			final var body = DisplaySource.parseBody(text(display.getGamlCode()), parser);
			for (int i = 0; i < body.layers().size(); i++) layers.add(identified(body.layers().get(i), i));
			trailing = body.trailingSource();
		} else {
			for (int i = 0; i < display.getLayers().size(); i++) {
				layers.add(DisplaySource.parseLayer(ModelGenerator.defineLayer(display.getLayers().get(i)), "layer:" + i, parser));
			}
			trailing = text(display.getTrailingSource());
		}
		final String renderer = display.getFacets().stream().filter(f -> "type".equals(f.getName()))
				.map(f -> text(f.getValue())).findFirst().orElse("");
		return new DisplayDraft(text(display.getName()), renderer, display.getFacets().stream()
				.filter(f -> !Set.of("name", "type").contains(f.getName())).map(f -> new Facet(f.getName(), text(f.getValue())))
				.toList(), layers, trailing);
	}

	public static void apply(final EDisplay display, final DisplayDraft draft) {
		final String error = draft.validationError();
		if (error != null) throw new IllegalArgumentException(error);
		final var initial = snapshot(display);
		final String kindError = kindError(initial, draft);
		if (kindError != null) throw new IllegalArgumentException(kindError);
		if (initial.equals(draft)) return;
		final boolean bodyChanged = !initial.layers().equals(draft.layers())
				|| !initial.trailingSource().equals(draft.trailingSource());
		DisplaySource.Body body = null;
		if (bodyChanged) {
			final var parser = parser();
			body = DisplaySource.parseBody(DisplaySource.renderBody(draft.layers(), draft.trailingSource(), parser), parser);
			if (body.layers().size() != draft.layers().size()) {
				throw new IllegalArgumentException("Layer boundaries changed the number of declarations.");
			}
		}
		final var values = new ArrayList<>(draft.facets());
		if (!draft.renderer().isBlank()) values.add(new Facet("type", draft.renderer()));
		final var oldFacets = new LinkedHashMap<String, gama.ui.diagram.metamodel.EFacet>();
		for (final var facet : display.getFacets()) oldFacets.put(facet.getName(), facet);
		final var facets = values.stream().map(value -> {
			var facet = oldFacets.get(value.name());
			if (facet == null) facet = GamaFactory.eINSTANCE.createEFacet();
			facet.setName(value.name());
			facet.setValue(value.value());
			return facet;
		}).toList();
		display.setName(draft.name());
		display.getFacets().clear();
		display.getFacets().addAll(facets);
		if (!bodyChanged) return;
		final var previous = new ArrayList<>(display.getLayers());
		final var byId = new LinkedHashMap<String, ELayer>();
		if (!display.isDefineGamlCode()) {
			for (int i = 0; i < previous.size(); i++) byId.put("layer:" + i, previous.get(i));
		}
		final List<ELayer> layers = new ArrayList<>();
		for (int i = 0; i < draft.layers().size(); i++) {
			final var value = draft.layers().get(i);
			var layer = byId.remove(value.id());
			final String source = body.layers().get(i).source();
			final boolean unchanged = layer != null && initial.layers().stream().anyMatch(value::equals)
					&& ModelGenerator.defineLayer(layer).equals(source);
			if (layer == null) layer = GamaFactory.eINSTANCE.createELayer();
			if (!unchanged) {
				layer.setDisplay(display);
				layer.setType(value.kind().name().toLowerCase(java.util.Locale.ROOT));
				layer.setName(value.expression());
				layer.setGamlCode(source);
			}
			if (layer.eResource() == null && display.eResource() != null) {
				if (display.getModel() == null) display.eResource().getContents().add(layer);
				else display.getModel().getObjects().add(layer);
			}
			layers.add(layer);
		}
		display.getLayers().clear();
		display.getLayers().addAll(layers);
		display.setDefineGamlCode(false);
		display.setTrailingSource(body.trailingSource());
		for (final var old : previous) {
			if (!layers.contains(old)) {
				for (final var data : List.copyOf(old.getChartlayers())) EcoreUtil.delete(data, true);
				EcoreUtil.delete(old, true);
			}
		}
	}

	public static String validate(final IFeatureProvider fp, final Diagram diagram, final EDisplay display,
			final EExperiment creationOwner, final DisplayDraft draft) {
		final String error = draft.validationError();
		if (error != null) return error;
		if (display != null) {
			final String kindError = kindError(snapshot(display), draft);
			if (kindError != null) return kindError;
		}
		final var owner = creationOwner == null ? display.getDisplayLink().getExperiment() : creationOwner;
		if (owner == null || owner.eResource() != diagram.eResource()
				|| creationOwner == null && display.eResource() != diagram.eResource()) {
			return "The display or its experiment is no longer part of this diagram.";
		}
		if (owner.getDisplayLinks().stream().anyMatch(link -> link.getDisplay() != display
				&& draft.name().equals(link.getDisplay().getName()))) return "A display with this name already exists.";
		final String source;
		try {
			source = candidateModel(fp, diagram, display, creationOwner, draft);
		} catch (final IllegalArgumentException invalidSource) {
			return invalidSource.getMessage();
		}
		if (source.isBlank()) return "The diagram needs a world box before a display can be validated.";
		return DiagramDraftValidation.validate(source, previewURI(diagram));
	}

	private static String kindError(final DisplayDraft initial, final DisplayDraft draft) {
		for (final var row : draft.layers()) {
			if (initial.layers().stream().anyMatch(old -> old.id().equals(row.id()) && old.kind() != row.kind())) {
				return "An existing layer cannot change its kind. Add a new layer instead.";
			}
		}
		return null;
	}

	static String candidateModel(final IFeatureProvider fp, final Diagram diagram, final EDisplay display,
			final EExperiment creationOwner, final DisplayDraft draft) {
		final var copier = new EcoreUtil.Copier(true, false);
		final var roots = copier.copyAll(diagram.eResource().getContents());
		copier.copyReferences();
		final var resource = new XMIResourceImpl(diagram.eResource().getURI());
		resource.getContents().addAll(roots);
		final EDisplay target;
		if (creationOwner == null) {
			target = (EDisplay) copier.get(display);
			if (target == null) throw new IllegalArgumentException("The display is no longer part of this diagram.");
		} else {
			target = GamaFactory.eINSTANCE.createEDisplay();
			attach((EExperiment) copier.get(creationOwner), target);
		}
		apply(target, draft);
		return ModelGenerator.generateModel(fp, (Diagram) copier.get(diagram), null);
	}

	public static ActionBodyContext context(final IFeatureProvider fp, final Diagram diagram, final EDisplay display,
			final EExperiment creationOwner, final DisplayDraft draft) {
		return new ActionBodyContext(previewURI(diagram), candidateModel(fp, diagram, display, creationOwner, draft), "");
	}

	private static URI previewURI(final Diagram diagram) {
		return diagram.eResource().getURI().trimFragment().trimFileExtension().appendFileExtension("display-wizard.gaml");
	}

	public static EDisplayLink attach(final EExperiment owner, final EDisplay display) {
		final var link = GamaFactory.eINSTANCE.createEDisplayLink();
		link.setSource(owner);
		link.setTarget(display);
		link.setExperiment(owner);
		link.setDisplay(display);
		owner.getDisplayLinks().add(link);
		display.setDisplayLink(link);
		if (owner.getModel() == null) owner.eResource().getContents().addAll(List.of(display, link));
		else {
			owner.getModel().getObjects().add(display);
			owner.getModel().getLinks().add(link);
		}
		return link;
	}

	public static List<String> speciesNames(final Diagram diagram, final boolean gridsOnly) {
		final List<String> names = new ArrayList<>();
		final var contents = diagram.eResource().getAllContents();
		while (contents.hasNext()) {
			if (contents.next() instanceof gama.ui.diagram.metamodel.ESpecies species
					&& !(species instanceof EExperiment) && !(species instanceof gama.ui.diagram.metamodel.EWorldAgent)
					&& !(species instanceof gama.ui.diagram.metamodel.EClassSpecies)
					&& (!gridsOnly || species instanceof gama.ui.diagram.metamodel.EGrid)) names.add(species.getName());
		}
		return names.stream().distinct().sorted().toList();
	}

	public static List<String> aspectNames(final Diagram diagram, final String speciesName) {
		final var names = new java.util.LinkedHashSet<String>();
		final var contents = diagram.eResource().getAllContents();
		while (contents.hasNext()) {
			if (contents.next() instanceof gama.ui.diagram.metamodel.ESpecies species && speciesName.equals(species.getName())) {
				final var visited = new java.util.HashSet<gama.ui.diagram.metamodel.ESpecies>();
				for (var current = species; current != null && visited.add(current); current = current.getInheritsFrom()) {
					for (final var link : current.getAspectLinks()) {
						if (link.getAspect() != null && link.getAspect().getName() != null) names.add(link.getAspect().getName());
					}
				}
			}
		}
		return List.copyOf(names);
	}
}
