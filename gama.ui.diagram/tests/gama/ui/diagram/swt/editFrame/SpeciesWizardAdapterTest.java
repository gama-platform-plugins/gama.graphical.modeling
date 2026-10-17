package gama.ui.diagram.swt.editFrame;

import java.lang.reflect.Proxy;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.PictogramsFactory;
import org.eclipse.graphiti.services.Graphiti;

import gama.ui.diagram.metamodel.GamaFactory;
import gama.api.compilation.descriptions.IModelDescription;
import gaml.compiler.resource.GamlResource;
import gaml.compiler.resource.GamlResourceServices;
import gaml.compiler.ui.wizards.species.SpeciesDraft;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Facet;
import gaml.compiler.ui.wizards.species.SpeciesDraft.Variable;

public final class SpeciesWizardAdapterTest {
	private static void check(final boolean value, final String message) {
		if (!value) throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		final var factory = GamaFactory.eINSTANCE;
		final var domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		try {
			final var resource = new XMIResourceImpl(URI.createURI("memory:/wizard.gadl"));
			domain.getResourceSet().getResources().add(resource);
			final var diagram = Graphiti.getPeCreateService().createDiagram("gamaDiagram", "wizard");
			final var world = factory.createEWorldAgent();
			world.setName("world");
			final var species = factory.createESpecies();
			species.setName("animal");
			species.setInit("write \"initial\";");
			final var variable = factory.createEVariable();
			variable.setName("energy");
			variable.setType("float");
			variable.setInit("1.0");
			final var facet = factory.createEFacet();
			facet.setName("frequency");
			facet.setValue("1");
			final var parent = factory.createESpecies();
			parent.setName("ancestor");
			final var action = factory.createEAction();
			action.setName("stay");
			final var actionLink = factory.createEActionLink();
			final var link = factory.createESubSpeciesLink();
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override
				protected void doExecute() {
					resource.getContents().addAll(List.of(diagram, world, species, parent, action, actionLink, link));
					species.getSkills().add("moving");
					species.getVariables().add(variable);
					species.getFacets().add(facet);
					species.setInheritsFrom(parent);
					actionLink.setSpecies(species);
					actionLink.setAction(action);
					species.getActionLinks().add(actionLink);
					action.getActionLinks().add(actionLink);
					link.setMacro(world);
					link.setMicro(species);
					world.getMicroSpeciesLinks().add(link);
					species.getMacroSpeciesLinks().add(link);
					final var shape = Graphiti.getPeCreateService().createContainerShape(diagram, true);
					final var pictogramLink = PictogramsFactory.eINSTANCE.createPictogramLink();
					pictogramLink.getBusinessObjects().add(world);
					shape.setLink(pictogramLink);
					diagram.getPictogramLinks().add(pictogramLink);
				}
			});
			final var initial = SpeciesWizardAdapter.snapshot(species);
			final var mirrored = new SpeciesDraft("observer", List.of(), List.of(new Facet("mirrors", "animal")),
					List.of(), "", List.of());
			final var mirrorAttributes = SpeciesWizardAdapter.localAttributes(species, mirrored);
			check(mirrorAttributes.size() == 1 && "target".equals(mirrorAttributes.get(0).variable().name())
					&& "animal".equals(mirrorAttributes.get(0).variable().type()),
					"Mirror must expose only target typed as the mirrored species, not its energy attribute");
			check(initial.variables().get(0).init().equals("1.0"), "Variable snapshot incomplete");
			final var changed = new SpeciesDraft("creature", List.of("moving"),
					List.of(new Facet("frequency", "2"), new Facet("parent", "ancestor")),
					List.of(new Variable("energy", "float", "2.0", "energy + 1", "", "0.0", "100.0")),
					"write \"changed\";", List.of());
			final var fp = (IFeatureProvider) Proxy.newProxyInstance(IFeatureProvider.class.getClassLoader(),
					new Class<?>[] { IFeatureProvider.class }, (proxy, method, arguments) -> {
						if (method.getName().equals("getBusinessObjectForPictogramElement")) {
							return Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement(
									(PictogramElement) arguments[0]);
						}
						throw new UnsupportedOperationException(method.getName());
					});
			check(!gama.ui.diagram.features.modelgeneration.ModelGenerator.generateModel(fp, diagram, null).isBlank(),
					"Original diagram fixture has no world");
			final String preview = SpeciesWizardAdapter.candidateModel(fp, diagram, species, null, changed);
			check(preview.contains("species creature parent:ancestor") && preview.contains("frequency:2")
					&& preview.contains("write \"changed\";") && preview.contains("action stay"),
					"Preview lost species context or body: " + preview);
			check(initial.equals(SpeciesWizardAdapter.snapshot(species)), "Preview mutated live diagram");
			final var added = new SpeciesDraft("new_species", List.of(), List.of(), List.of(), "", List.of());
			check(SpeciesWizardAdapter.candidateModel(fp, diagram, null, world, added).contains("species new_species"),
					"Creation preview missing");
			check(world.getMicroSpeciesLinks().size() == 1 && resource.getContents().size() == 7,
					"Cancelled creation left live objects");
			domain.getCommandStack().execute(new RecordingCommand(domain, "Edit species") {
				@Override
				protected void doExecute() {
					SpeciesWizardAdapter.apply(species, changed);
				}
			});
			check(changed.equals(SpeciesWizardAdapter.snapshot(species)), "Draft did not round-trip");
			check(species.getVariables().get(0) == variable && species.getFacets().get(0) == facet,
					"Unchanged member identities replaced");
			check(species.getInheritsFrom() == parent && species.getActionLinks().get(0) == actionLink
					&& species.getMacroSpeciesLinks().get(0) == link, "Linked graph changed");
			domain.getCommandStack().undo();
			check(initial.equals(SpeciesWizardAdapter.snapshot(species)), "Undo did not restore complete draft");
			check(variable.getInit().equals("1.0") && facet.getValue().equals("1"), "Undo lost member identities");
			domain.getCommandStack().redo();
			check(changed.equals(SpeciesWizardAdapter.snapshot(species)), "Redo did not restore draft");
			check(SpeciesWizardAdapter.validate(fp, diagram, species, null,
					new SpeciesDraft("creature", List.of(), List.of(new Facet("parent", "bad")),
							List.of(), "", List.of())) != null, "Parent facet should be relation-managed");
			checkValidationLifecycle(fp, diagram, species, changed);
			checkParents(domain, diagram, species, parent, changed);
			checkGrid(domain, diagram, fp);
			checkWorld(domain, diagram, world, fp);
			System.out.println("PASS: detached snapshots, isolated edit/create previews, identity/context preservation, "
					+ "undo/redo and isolated compiler-resource lifecycle");
		} finally {
			domain.dispose();
		}
	}

		private static void checkWorld(final TransactionalEditingDomain domain,
				final org.eclipse.graphiti.mm.pictograms.Diagram diagram,
				final gama.ui.diagram.metamodel.EWorldAgent world, final IFeatureProvider fp) {
			final var initial = SpeciesWizardAdapter.modelSnapshot(diagram, world);
			final var doc = new gaml.compiler.ui.wizards.species.ModelDocumentation("Author", "tag", "Details */ retained");
			final var draft = new SpeciesDraft("renamed_model", initial.skills(),
					List.of(new Facet("torus", "true"), new Facet("frequency", "2")), initial.variables(),
					"write \"keep init\";", initial.reflexOrder());
			final String preview = SpeciesWizardAdapter.candidateModel(fp, diagram, world, null, draft);
			check(preview.contains("model renamed_model") && preview.contains("global torus:true frequency:2"),
					"Model preview not generated from world values");
			check(initial.equals(SpeciesWizardAdapter.modelSnapshot(diagram, world)), "Model preview mutated world");
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override
				protected void doExecute() {
					SpeciesWizardAdapter.applyModel(diagram, world, draft);
					gama.ui.diagram.features.modelgeneration.DiagramModelDocumentation.write(diagram, doc);
				}
			});
			check(world.getName().equals("world") && draft.equals(SpeciesWizardAdapter.modelSnapshot(diagram, world)),
					"Model rename changed world identity/name");
			final String generated = gama.ui.diagram.features.modelgeneration.ModelGenerator.generateModel(fp, diagram, null);
			check(doc.equals(gaml.compiler.ui.wizards.species.ModelDocumentation.read(generated)),
					"Generated GAML lost documentation");
			try {
				final var output = new java.io.ByteArrayOutputStream();
				diagram.eResource().save(output, Map.of());
				final var reloaded = new XMIResourceImpl(URI.createURI("memory:/reloaded.gadl"));
				reloaded.load(new java.io.ByteArrayInputStream(output.toByteArray()), Map.of());
				final var savedDiagram = (org.eclipse.graphiti.mm.pictograms.Diagram) reloaded.getContents().get(0);
				check(doc.equals(gama.ui.diagram.features.modelgeneration.DiagramModelDocumentation.read(savedDiagram)),
						"XMI lost model documentation");
			} catch (final IOException error) {
				throw new AssertionError(error);
			}
			domain.getCommandStack().undo();
			check(initial.equals(SpeciesWizardAdapter.modelSnapshot(diagram, world))
					&& gama.ui.diagram.features.modelgeneration.DiagramModelDocumentation.read(diagram).isEmpty(),
					"Model edit undo lost name or documentation");
			domain.getCommandStack().redo();
			check(doc.equals(gama.ui.diagram.features.modelgeneration.DiagramModelDocumentation.read(diagram)),
					"Model edit redo lost documentation");
		}

		private static void checkGrid(final TransactionalEditingDomain domain,
				final org.eclipse.graphiti.mm.pictograms.Diagram diagram, final IFeatureProvider fp) {
			final var grid = GamaFactory.eINSTANCE.createEGrid();
			grid.setName("cells");
			grid.setInit("write \"preserved\";");
			final var width = GamaFactory.eINSTANCE.createEFacet();
			width.setName("width");
			width.setValue("10");
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override
				protected void doExecute() {
					diagram.eResource().getContents().add(grid);
					grid.getFacets().add(width);
				}
			});
			final var initial = SpeciesWizardAdapter.snapshot(grid);
			final var draft = new SpeciesDraft("cells", List.of(), List.of(
					new Facet("width", "40"), new Facet("height", "30"), new Facet("neighbors", "6"),
					new Facet("file", "file(\"cells.asc\")"), new Facet("files", "[file(\"other.asc\")]"),
					new Facet("use_individual_shapes", "false"), new Facet("use_neighbors_cache", "true"),
					new Facet("use_regular_agents", "false")), List.of(), initial.init(), initial.reflexOrder());
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override
				protected void doExecute() { SpeciesWizardAdapter.apply(grid, draft); }
			});
			check(draft.equals(SpeciesWizardAdapter.snapshot(grid)) && grid.getFacets().get(0) == width,
					"Grid settings lost or replaced existing facets");
			domain.getCommandStack().undo();
			check(initial.equals(SpeciesWizardAdapter.snapshot(grid)), "Grid undo lost original declaration");
			domain.getCommandStack().redo();
			check(draft.equals(SpeciesWizardAdapter.snapshot(grid)), "Grid redo failed");
			check("A grid cannot mirror another species.".equals(SpeciesWizardAdapter.validate(fp, diagram, grid, null,
					new SpeciesDraft("cells", List.of(), List.of(new Facet("mirrors", "creature")), List.of(), "", List.of()))),
					"Grid accepted a mirror species");
		}

		private static void checkParents(final TransactionalEditingDomain domain,
				final org.eclipse.graphiti.mm.pictograms.Diagram diagram,
				final gama.ui.diagram.metamodel.ESpecies species,
				final gama.ui.diagram.metamodel.ESpecies parent, final SpeciesDraft changed) {
			final var factory = GamaFactory.eINSTANCE;
			final var other = factory.createESpecies();
			other.setName("other");
			final var descendant = factory.createESpecies();
			descendant.setName("descendant");
			final var pe = Graphiti.getPeCreateService();
			final var fp = (IFeatureProvider) Proxy.newProxyInstance(IFeatureProvider.class.getClassLoader(),
					new Class<?>[] { IFeatureProvider.class }, (proxy, method, arguments) -> {
						if (method.getName().equals("addIfPossible")) {
							final var context = (org.eclipse.graphiti.features.context.IAddConnectionContext) arguments[0];
							final var connection = pe.createFreeFormConnection(diagram);
							connection.setStart(context.getSourceAnchor());
							connection.setEnd(context.getTargetAnchor());
							final var link = PictogramsFactory.eINSTANCE.createPictogramLink();
							link.getBusinessObjects().add((org.eclipse.emf.ecore.EObject) context.getNewObject());
							connection.setLink(link);
							diagram.getPictogramLinks().add(link);
							return connection;
						}
						throw new UnsupportedOperationException(method.getName());
					});
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override
				protected void doExecute() {
					diagram.eResource().getContents().addAll(List.of(other, descendant));
					descendant.setInheritsFrom(species);
					for (final var node : List.of(species, parent, other)) {
						final var shape = pe.createContainerShape(diagram, true);
						final var link = PictogramsFactory.eINSTANCE.createPictogramLink();
						link.getBusinessObjects().add(node);
						shape.setLink(link);
						diagram.getPictogramLinks().add(link);
						pe.createChopboxAnchor(shape);
					}
					SpeciesWizardAdapter.syncParentConnection(fp, diagram, species, List.of());
				}
			});
			final var relation = SpeciesWizardAdapter.parentConnections(diagram, species).get(0);
			final var modelLink = parent.getInheritingLinks().get(0);
			check(SpeciesWizardAdapter.parentNames(species, species).contains("agent")
					&& !SpeciesWizardAdapter.parentNames(species, species).contains("descendant")
					&& !SpeciesWizardAdapter.parentNames(species, species).contains(species.getName()), "Unsafe parent choices");
		check(SpeciesWizardAdapter.mirrorNames(species).contains("descendant")
					&& SpeciesWizardAdapter.mirrorNames(species).contains(species.getName()),
					"Mirror choices incorrectly use inheritance cycle filtering");
		check(SpeciesWizardAdapter.wizardContext(species).parentNames(
					List.of("agent", "descendant", species.getName(), "other")).equals(List.of("agent", "other")),
					"Merged built-in/local parent choices bypass local cycle filtering");
		check(SpeciesWizardAdapter.wizardContext(species, null).parentNames(List.of(species.getName()))
					.contains(species.getName()), "Creation incorrectly excludes containing species as parent");
			final var retargeted = new SpeciesDraft(changed.name(), changed.skills(),
					List.of(new Facet("frequency", "2"), new Facet("parent", "other")),
					changed.variables(), changed.init(), changed.reflexOrder());
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override
				protected void doExecute() {
					final var previous = SpeciesWizardAdapter.parentConnections(diagram, species);
					SpeciesWizardAdapter.apply(species, retargeted);
					SpeciesWizardAdapter.syncParentConnection(fp, diagram, species, previous);
				}
			});
			check(species.getInheritsFrom() == other && other.getInheritingLinks().contains(modelLink)
					&& parent.getInheritingLinks().isEmpty(), "Parent references not retargeted");
			check(SpeciesWizardAdapter.parentConnections(diagram, species).get(0) == relation
					&& relation.getStart().getParent().getLink().getBusinessObjects().contains(other),
					"Visible relation not retargeted or identity lost");
			domain.getCommandStack().undo();
			check(species.getInheritsFrom() == parent && parent.getInheritingLinks().contains(modelLink)
					&& relation.getStart().getParent().getLink().getBusinessObjects().contains(parent), "Parent undo failed");
			domain.getCommandStack().redo();
			final var cleared = new SpeciesDraft(changed.name(), changed.skills(), List.of(new Facet("frequency", "2")),
					changed.variables(), changed.init(), changed.reflexOrder());
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override
				protected void doExecute() {
					final var previous = SpeciesWizardAdapter.parentConnections(diagram, species);
					SpeciesWizardAdapter.apply(species, cleared);
					SpeciesWizardAdapter.syncParentConnection(fp, diagram, species, previous);
				}
			});
			check(species.getInheritsFrom() == null && !diagram.getConnections().contains(relation)
					&& other.getInheritingLinks().isEmpty(), "Default agent left a stale parent relation");
			domain.getCommandStack().undo();
			check(species.getInheritsFrom() == other && diagram.getConnections().contains(relation)
					&& modelLink.getChild() == species, "Removing parent was not undoable");
			check(SpeciesWizardAdapter.validate(fp, diagram, species, null,
					new SpeciesDraft(changed.name(), changed.skills(), List.of(new Facet("parent", "descendant")),
							changed.variables(), changed.init(), List.of())).contains("descendants"), "Cycle accepted");
			checkBuiltInParent(domain, diagram, fp, species, other, relation, changed);
		}

		@SuppressWarnings("unchecked")
		private static void checkBuiltInParent(final TransactionalEditingDomain domain,
				final org.eclipse.graphiti.mm.pictograms.Diagram diagram, final IFeatureProvider fp,
				final gama.ui.diagram.metamodel.ESpecies species, final gama.ui.diagram.metamodel.ESpecies localParent,
				final org.eclipse.graphiti.mm.pictograms.Connection relation, final SpeciesDraft changed) {
			try {
				final var field = gama.api.kernel.GamaMetaModel.class.getDeclaredField("builtInSpeciesDescriptions");
				field.setAccessible(true);
				final var instance = gama.api.kernel.GamaMetaModel.class.getDeclaredField("INSTANCE");
				instance.setAccessible(true);
				final var registry = (Map<String, gama.api.compilation.descriptions.ISpeciesDescription>)
						field.get(instance.get(null));
				final String name = "__wizard_test_builtin_parent__";
				final var builtin = (gama.api.compilation.descriptions.ISpeciesDescription) Proxy.newProxyInstance(
						IFeatureProvider.class.getClassLoader(),
						new Class<?>[] { gama.api.compilation.descriptions.ISpeciesDescription.class },
						(proxy, method, arguments) -> switch (method.getName()) {
							case "getName" -> name;
							case "isModel", "isExperiment" -> false;
							default -> throw new AssertionError("Unexpected registry call: " + method.getName());
						});
				final var previous = registry.put(name, builtin);
				try {
					final var draft = new SpeciesDraft(changed.name(), changed.skills(),
							List.of(new Facet("frequency", "2"), new Facet("parent", name)),
							changed.variables(), changed.init(), changed.reflexOrder());
					domain.getCommandStack().execute(new RecordingCommand(domain) {
						@Override
						protected void doExecute() {
							final var connections = SpeciesWizardAdapter.parentConnections(diagram, species);
							SpeciesWizardAdapter.apply(species, draft);
							SpeciesWizardAdapter.syncParentConnection(fp, diagram, species, connections);
						}
					});
					check(species.getInheritsFrom() == null && !diagram.getConnections().contains(relation)
							&& draft.equals(SpeciesWizardAdapter.snapshot(species)),
							"Built-in selection did not replace local parent with explicit facet");
					domain.getCommandStack().undo();
					check(species.getInheritsFrom() == localParent && diagram.getConnections().contains(relation),
							"Built-in parent undo lost local relation");
					domain.getCommandStack().redo();
					check(draft.equals(SpeciesWizardAdapter.snapshot(species)), "Built-in parent redo failed");
				} finally {
					if (previous == null) registry.remove(name); else registry.put(name, previous);
				}
			} catch (final ReflectiveOperationException error) {
				throw new AssertionError("Cannot initialize isolated built-in registry fixture", error);
			}
		}

	private static void checkValidationLifecycle(final IFeatureProvider fp,
			final org.eclipse.graphiti.mm.pictograms.Diagram diagram,
			final gama.ui.diagram.metamodel.ESpecies species, final SpeciesDraft changed) {
		final var factories = Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap();
		final Object previous = factories.get("gaml");
		final boolean[] disposed = { false };
		final boolean[] unloaded = { false };
		final boolean[] failParsing = { false };
		final int[] toolbarCalls = { 0 };
		final URI realURI = diagram.eResource().getURI().trimFileExtension().appendFileExtension("gaml");
		GamlResourceServices.addResourceListener(realURI, (model, experiments, context) -> toolbarCalls[0]++);
		final Resource.Factory factory = uri -> {
			final var result = new GamlResource() {
				@Override
				public boolean hasErrors() { return failParsing[0]; }

				@Override
				protected void doLoad(final InputStream stream, final Map<?, ?> options) throws IOException {
					check(getURI().toString().endsWith(".species-wizard.gaml"), "Preview URI not isolated");
					check(new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("species creature"),
							"Wrong candidate loaded");
					if (failParsing[0]) getErrors().add(new Resource.Diagnostic() {
						@Override public String getMessage() { return "Invalid candidate syntax"; }
						@Override public String getLocation() { return getURI().toString(); }
						@Override public int getLine() { return 1; }
						@Override public int getColumn() { return 1; }
					});
				}

				@Override
				public IModelDescription buildCompleteDescription() {
					return (IModelDescription) Proxy.newProxyInstance(IModelDescription.class.getClassLoader(),
							new Class<?>[] { IModelDescription.class }, (proxy, method, arguments) -> switch (method.getName()) {
								case "validate" -> proxy;
								case "dispose" -> { disposed[0] = true; yield null; }
								default -> throw new AssertionError("Unexpected compiler call: " + method.getName());
							});
				}

				@Override
				protected void doUnload() {
					unloaded[0] = true;
				}
			};
			result.setURI(uri);
			return result;
		};
		factories.put("gaml", factory);
		try {
			check(SpeciesWizardAdapter.validate(fp, diagram, species, null, changed) == null,
					"Valid candidate rejected");
			check(disposed[0] && unloaded[0], "Candidate description/resource not released");
			failParsing[0] = true;
			check("Invalid candidate syntax".equals(
					SpeciesWizardAdapter.validate(fp, diagram, species, null, changed)), "Parse error hidden");
			check(toolbarCalls[0] == 0, "Preview modified full-model toolbar");
			check(changed.equals(SpeciesWizardAdapter.snapshot(species)), "Validation mutated live model");
		} finally {
			if (previous == null) factories.remove("gaml"); else factories.put("gaml", previous);
			GamlResourceServices.removeResourceListener(realURI);
		}
	}
}
