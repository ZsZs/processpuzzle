## Context Object

A **context object** is an entity you pick once and then work *inside*, instead of opening it again every time a different part of it is edited.

This page has three entities that exist only for this demonstration: an **aggregate root** (*Demo Root*) and two kinds of part it embeds (*Demo Component One* and *Demo Component Two*). The parts are stored inside the root, so without a context you would open the root's form, find the part in its list, open it, and go back — every time.

### Try it

1. Click **Aggregate Root**. This is the ordinary list of roots. Click **Set as active** on a row: that root is now the context object, and you land on the first part.
2. The bar above the buttons shows the context object. Its menu switches to another root, opens the root for editing, or clears the context.
3. Click **Embedded Component One** or **Two** to list that kind of part *of the selected root*. **Add**, or the edit button of a row, opens the part's own form — you never choose the root again. Saving writes that part alone.
4. Open **Aggregate Root** again and then a root's details: its parts are listed there as well. Use the arrow next to a list's name to hide it when it is long; it only changes what is shown.

### What to notice

- The selected root is in the **address** (`…/ctx/demo-root/<id>/…`), so a refresh, a bookmark or a second browser tab shows the same root. The last selection is remembered on this device, which is what the part buttons use when the address names none.
- A part's form is the same form it has everywhere; only the way back and the saving differ. Nothing here is specific to these entities: any entity that embeds other entities can be a context object.
