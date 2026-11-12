# Graphical modeling

## Live diagrams from GAML

**Switch to diagram** opens a live, one-way view of the current GAML editor
document, including unsaved edits. Opening it does not create a `.gadl` file,
a `diagrams` folder, or a generated GAML file. Repeating the command focuses
the existing live view for that editor.

Edit the model in the GAML editor. The diagram refreshes after a short delay;
semantic creation, editing, deletion and reconnection are disabled in this mode.
Selection, movement, resizing, colors and layout remain presentation operations.
Box positions, sizes, colors and connection bends are retained for declarations
that still have the same source file, kind, name and enclosing declarations.
Renamed or moved declarations may be laid out as new nodes.
Presentation undo history is cleared when a new source projection is applied.

The live view uses the single-selection tool without a palette. The toolbar
provides zoom in, fit and zoom out controls in both live and editable diagrams.
Diagrams initially fit the view and continue to fit when it is resized. Manual
zooming or dragging the background leaves fit mode; use **Fit** to restore it.
Dragging empty space pans the canvas without marquee selection.

While the source is invalid, the last valid diagram remains visible with an
explicit status message. Experiment controls are unavailable until the view is
current again. Validation and launching use the original GAML context rather
than a generated model with a different URI.

The dirty indicator reflects unsaved GAML changes, not diagram layout changes.
**Save** saves the source document. Closing the live view does not save or
discard the source, and closing the source closes its live view. A live view
is not restored as a standalone diagram after a workbench restart.

## Independent snapshots

Choose the **Edit** icon in the live toolbar to save
an independent `.gadl` copy and immediately open it with the full GAMA editing
palette. A confirmation explains that edits to this copy do not update the
GAML source. Cancelling either dialog leaves the live view unchanged.

Use **Save As** to choose a workspace `.gadl` file. The snapshot contains a detached copy of
the last successfully displayed model and its current presentation, even if
the source currently has errors.

Saving a snapshot does not save the GAML document, replace the live editor
input, or stop synchronization. Open the saved file separately to edit it as
a standalone diagram. It has no synchronization link to the original GAML
editor; subsequent changes on either side do not affect the other.

Existing `.gadl` files and the **Model diagram** creation wizard retain their
standalone editing and GAML export behavior.

**Layout** arranges the whole diagram top-down using GAMA's Jungrapht
Sugiyama layout, after fitting boxes to their contents. It requires a GAMA
installation whose `gama.dependencies` bundle exports Jungrapht.
