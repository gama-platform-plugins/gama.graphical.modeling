# Description-based diagram import

Diagram generation uses this pipeline:

`GamlResource.buildCompleteDescription()` -> validation -> `DescriptionDiagramImporter` -> `EGamaModel` -> Graphiti.

The description is read and copied while the source document is locked, then disposed. The diagram owns only its editable EMF snapshot, not compiler descriptions or executable species/statements. Nested species are visited recursively, and inheritance links are resolved after all nodes exist.

Source-backed facets and bodies use the original Xtext nodes, not post-validation
expression/statement serializers: compiled descriptions can contain non-GAML
operator notation, omit action names, or have consumed their children. Bodies
retain comments and formatting; expression comments become whitespace. The
serializer remains a fallback for descriptions without source nodes.
Only explicitly declared facets are copied from source-backed declarations.
Compiler-injected defaults such as experiment/display `title` labels must not be
written back as unquoted GAML expressions.
Equation systems use a dedicated AST assignment list rather than a normal
statement block. Their source bodies must still be imported in full, including
each `diff(variable, time)` declaration; an empty generated equation block can
parse successfully but fail when the ODE solver first runs.

Parameters and monitors are contained in the snapshot's objects list because their experiment lists are non-containment references. They are edited through the experiment, not rendered as separate diagram nodes. Save/reload checks cover both parameters and GUI/batch monitors.

Only source-backed declarations owned by the current description are imported. Built-in and synthetic members are excluded, as are inherited copies that still point to the parent's source declaration. Explicit overrides retain a distinct source declaration, so names such as `shape`, `name`, `seed`, or `die` are not blacklisted. Imported files are not filtered out by file location. Inherited user members remain visible on their declaring parent rather than being duplicated on every child.

## Regression checks

`DescriptionDiagramImporterTest` is a standalone Java entry point; it needs no additional test dependencies. From the repository root, set `GAMA_TARGET_CLASSPATH` to the binary jars of the plugin's resolved Eclipse/GAMA target platform, then run:

```sh
classes="$(mktemp -d)"
javac -proc:none -cp "$GAMA_TARGET_CLASSPATH" \
  -sourcepath gama.ui.diagram/src -d "$classes" \
  gama.ui.diagram/tests/gama/ui/diagram/features/modelgeneration/DescriptionDiagramImporterTest.java \
  gama.ui.diagram/tests/gama/ui/diagram/features/modelgeneration/ValidationIsolationTest.java
java -cp "$classes:$GAMA_TARGET_CLASSPATH" \
  gama.ui.diagram.features.modelgeneration.DescriptionDiagramImporterTest
java -cp "$classes:$GAMA_TARGET_CLASSPATH" \
  gama.ui.diagram.features.modelgeneration.ValidationIsolationTest
```

Remove the temporary classes directory after the run. Do not include source jars in the classpath.

The checks cover built-in exclusion, explicit overrides, inherited/reparented copies, imported declarations, recursive species, grids, forward inheritance references, component bodies/facets, action signatures, reflex ordering, experiments, parameters, output declarations, generated rule/parameter/experiment code, and XMI persistence. Strict description doubles fail on unexpected API calls, including attempts to compile executable symbols. These are importer contract checks, not a substitute for opening a real GAML model in the running Eclipse editor.

The importer regression also uses real parser nodes with empty description
bodies and deliberately invalid compiled facet serialization. It checks infix
operators, action calls, comments/braces, computed attributes and zero-argument
actions against the real GAML parser. Experiment/display cases include injected
multiword titles before a parameter, absent defaults, and retained explicit title
expressions. All stored code and layer settings must
survive EMF copying; their metamodel attributes are not derived.

`ValidationIsolationTest` reproduces the compiler's URI-scoped listener collision and checks that field-level syntax checks cannot clear the Run toolbar's experiments or share the full model's validation context. Syntax-check resources use a separate `.syntax-check.gaml` URI in the same directory, preserving the base for relative paths. Full-model validation still reports experiment additions/removals. This exercises compiler resource notifications, not GAML parsing or SWT rendering.

Full diagram validation delivers runnable experiments directly from the validated model description to the owning editor, before disposing the description. It does not register the editor in the global URI-keyed listener map. The regression also checks direct delivery when another listener owns that URI, abstract-experiment filtering, failed-description reporting, and disposal.

The graphical editor uses `ExperimentToolbar` and `GamlEditorState` from `gama.ui.editor`, shared with the text editor, for experiment controls and validation status. Build it against the corresponding GAMA toolbar extraction change; older target-platform versions do not contain that component. The validation regression checks that the toolbar state snapshots experiment names before description disposal. Launch callbacks receive full experiment names rather than display abbreviations.

## Outline icons

`GamaImageProvider` resolves the same `gaml/_<keyword>` SVG resources as GAMA's outline from the shared bundle. Palette IDs always use `icons_svg/light/`; diagram nodes use separate `.dark` IDs backed by `icons_svg/dark/`, independently of the workbench theme. Both variants are registered as bundle URLs with Graphiti, rather than copying assets into this plugin, and missing keyword assets fall back to the same theme's unknown icon. Editor-open migration corrects older semantic assignments and switches existing nodes to dark icons. Inheritance uses the shared `gaml/_hierarchy` icon, requiring the corresponding assets in `gama.ui.shared`. GAMA currently has no dedicated `plan` icon.

Node icons fit proportionally into 24-by-24 bounds, rather than clipping the SVG to the legacy 30-by-15 area. Editor-open migration also fixes existing icon bounds and scaling flags. `IconLayoutTest` exercises that migration through the common layout, checks that manual node positions are preserved, and checks actual Graphiti renderer scaling for square, wide and tall images. Compile it with the same target classpath/sourcepath and run its main class (on macOS, add `-XstartOnFirstThread`).

All palette compartment headers reuse GAMA's light `navigator/files/folder.model` SVG instead of Graphiti's default folder.

## Shared species wizard

Ordinary species boxes open the `SpeciesWizard` from the exported
`gaml.compiler.ui.wizards.species` package in `gama.ui.editor`. The species-creation
tool in the palette uses the same form. Grid boxes open its grid variant on
double-click; world boxes open the shared Model/global variant.

The shared immutable `SpeciesDraft` carries name, skills, facets, variables, init
body and reflex order, without compiler descriptions or Graphiti dependencies.
`SpeciesWizardAdapter` translates it to the diagram metamodel. The Parent selector
defaults to `agent`; choosing a diagram species updates both the inheritance
reference and its visible relation in the same undoable transaction. Self/descendant
parents and ambiguous names are rejected. Existing external/built-in parent facets
are preserved without inventing diagram boxes. Actions, aspects, nested species
and other linked components are preserved. Only linked reflexes can be reordered here.

Registered built-in parents are also offered by the shared wizard. Selecting one
stores an explicit parent facet without a local species box or relation. Local
species names take precedence over built-ins, including cycle/ambiguity checks.

The diagram supplies contextual inherited attributes to the shared form, combining
the shared built-in/skill/control metadata with local parent declarations (including
ancestors). Mirroring does not inherit attributes: it contributes only `target`,
typed as the selected mirror species. Inherited entries remain metadata until
explicitly redefined in the wizard.

Cancel leaves the diagram unchanged. Accepting an unchanged draft does not apply
an edit. On OK, the adapter first validates GAML generated from a complete copied
diagram under an isolated `.species-wizard.gaml` URI. It does not mutate the live
model or publish toolbar status during this preview. Full-model errors, including
pre-existing ones, must be resolved before accepting the draft. Accepted edits
use the diagram's transaction/undo mechanism; unchanged variables/facets retain
their object identities. Species creation is part of Graphiti's existing creation
transaction.

Build against the main-GAMA shared species wizard change as well as the shared
toolbar change. `SpeciesWizardAdapterTest` is a standalone main in
`gama.ui.diagram.swt.editFrame`; compile/run it using the target classpath above.
It covers snapshots, isolated create/edit previews, preserved linked components,
member identities, parent-facet protection, and real EMF undo/redo.

`DiagramWizardSourceTest` additionally runs the actual preview generator and GAML
parser for every world/species/grid/experiment in a saved `.gadl`:

```sh
javac -proc:none -cp "$classes:$GAMA_TARGET_CLASSPATH" \
  -sourcepath gama.ui.diagram/src -d "$classes" \
  gama.ui.diagram/tests/gama/ui/diagram/swt/editFrame/DiagramWizardSourceTest.java
java -cp "$classes:$GAMA_TARGET_CLASSPATH" \
  gama.ui.diagram.swt.editFrame.DiagramWizardSourceTest /path/to/model.gadl
java -cp "$classes:$GAMA_TARGET_CLASSPATH" \
  gama.ui.diagram.swt.editFrame.DiagramWizardSourceTest --source /path/to/model.gaml
```

The second mode parses a real library source, supplies source-backed description
doubles to the production importer, creates Graphiti links and saves/reloads XMI
in memory before generating unchanged previews. It is not a full platform
description-compiler or native dialog test. Both modes require the unchanged
preview to retain exactly the generated model's bodies and facets. An optional
last argument names an existing output directory for exact preview dumps. Inputs
are read-only. The SIR (Switch) library model exercises all three wizard kinds.
Source mode also compares every differential equation against the original
source after XMI save/reload and every preview copy, not just parser success.

Diagrams saved with the broken serializer already contain corrupted code, which
cannot safely be reconstructed from the `.gadl` alone. After rebuilding the
diagram plugin and restarting GAMA, back up and close the old diagram, open the
original GAML model, and click the **Switch to diagram** toolbar button to regenerate
it. This replaces the generated diagram (including its layout); there is no
automatic repair or validation bypass.

The Model/global form edits the diagram's model name without renaming its world
node. Container and file name display the current diagram resource and are
read-only. Author, tags and documentation are stored as Graphiti diagram properties,
survive `.gadl` save/reload, and are included in the generated GAML header.
Generating a diagram from a GAML document snapshots its recognized model header.
Documentation and model edits participate in the same undoable transaction.

`OutlineIconTest` checks all 16 node-type mappings and decodes the referenced light/dark SVG assets using SWT. Compile it with the same target classpath and sourcepath as above, then pass the resolved `gama.ui.shared` binary jar:

```sh
java --enable-native-access=ALL-UNNAMED -cp "$classes:$GAMA_TARGET_CLASSPATH" \
  gama.ui.diagram.features.modelgeneration.OutlineIconTest /path/to/gama.ui.shared.jar
```

## Shared action wizard

Action creation from **Agent features** or **Classes**, and double-click editing
of an action box, use `ActionWizard` from
`gaml.compiler.ui.wizards.action` in `gama.ui.editor`. Rebuild that bundle along
with this plugin. The old incremental action frame is no longer used by these
entry points.

The shared form edits the name, return type (`action` means no return), Virtual,
ordered arguments (name, type and optional default), and a syntax-highlighted
GAML body. Registered types and diagram species, grids and classes are offered.
The declaration header is displayed as a live label above the body-only editor,
not inside the code box. It follows name, return type, argument and Virtual
changes. The body editor is disabled for virtual actions without erasing its draft text.
Virtual declarations generate a semicolon, not an executable body; inactive
code remains stored in XMI so it can be restored by unchecking Virtual.

`ActionWizardAdapter` builds previews from a complete copied diagram. The
embedded editor gets the actual owner's context, including model attributes,
other actions, arguments and the requested return type. Its prefix and suffix
are split on a unique generated body marker, not source braces or action names.
Validation uses an isolated `.action-wizard.gaml` URI, preserving relative
imports without publishing experiment-toolbar state. Existing full-model
errors can also prevent acceptance; the preview is not a validation bypass.

Cancel and unchanged edits do not mutate the live action. Changed drafts are
applied in one undoable edit; creation uses Graphiti's existing transaction.
Stale model changes are rejected before applying the result. Existing actions,
links, unrelated facets and arguments with unchanged names keep their EMF
identities. Argument order/defaults, virtual state and inactive body text survive
save/reload.

`ActionWizardAdapterTest` covers world/species/class creation previews, existing
grid/experiment action contexts, generated syntax, virtual signatures, body
contexts, detached previews, XMI, argument/link identity, undo/redo and isolated
validation-resource and import-edge disposal. Compile
`DescriptionDiagramImporterTest.java` and `SpeciesWizardAdapterTest.java`
alongside it for the shared source-backed and standalone-workspace fixtures. As with the other diagram
adapter checks, parser/EMF tests and strict compiler-resource doubles do not
replace testing the shared native dialog in a running GAMA workbench.

## Classes

Classes are stored as `EClassSpecies`, an additive EMF subtype that reuses
species drawing, attribute/action editing, layout, update and deletion services.
Existing classifier IDs and species serialization are unchanged. Classes are
imported from class descriptions (not executable objects), including the model's
class visitor, and generated as top-level `class` declarations without an agent
`init` block.

The **Classes** palette compartment offers **has class**, **is parent of** and
**has the action**. Action creation uses the same service as species and remains
available in **Agent features** too.
Only the world can own classes. Class inheritance is class-only, defaults to
`object`, and excludes self/descendants; species and grids cannot inherit classes
or `object`. Class boxes use a lavender fill and the shared class outline icon;
ownership links use the matching class foreground hue, including custom colors.
They open the shared `Kind.CLASS` form: Name, Parent, Virtual and Attributes, with
local inherited attributes but no agent skills, control or scheduling fields.
Actions remain separate linked boxes. Other behavior/component creation tools
are unavailable from classes.

Build against the shared wizard's `Kind.CLASS`, `SpeciesDraft.parent(String)`
and `SpeciesWizardContext.classBuiltIns()` APIs in `gama.ui.editor`.
`gama.ui.diagram.swt.editFrame.ClassDiagramTest` covers class import, forward
inheritance, typed action signatures/bodies, consumed and implicit virtual action
flags, XMI subtype/reference persistence,
generated syntax, unchanged/creation previews, parent filtering, invalid drafts,
world-only containment, preserved action identity, transaction undo/redo and
palette contents/eligibility and shared edit/delete service dispatch. Compile
`DescriptionDiagramImporterTest.java` alongside it (the source-backed fixture is
shared) and run its standalone main like the other adapter checks.
Source-backed checks use description doubles, not a full running GAMA
compiler/runtime. `DiagramWizardSourceTest --source` also accepts the library's
Define Classes recipes and exercises every class preview after XMI save/reload.

New nodes without an explicit requested size are fitted to their name, icon and
attributes using the same calculation as **Layout**. Existing manually sized
nodes are not shrunk by normal updates. `NewElementAppearanceTest` exercises all
16 node add paths, explicit/manual size preservation, and class ownership color
updates with real SWT/Graphiti services.

## Diagram context menu

The diagram has a closed context menu: Undo, Delete; Update, Layout; Export as
GAML, Export... (semicolons denote separators). It is not registered for
additional Eclipse contributions. Standard actions retain their registry
instances and enablement; Layout and GAML export use Graphiti feature commands,
preserving transactional execution. Keyboard shortcuts and toolbar commands are
not removed.

`gama.ui.diagram.editor.GamaDiagramContextMenuTest` checks the exact order,
separators, registered action identity, dynamic enablement and disabled
workbench contributions. Compile/run its standalone main with the same target
classpath and sourcepath as the other checks.
