# Welcome to the Demo application

This page is a **WIDGETS route**: the content area renders the widget instances the
`Demo` application definition declares for the path `home` — here a single `markdown-page`
pointed at this file.

The rest of what you see comes from the same definition:

- the **header** carries the application's name and logo, plus its own chrome widgets —
  a language selector, a like button and a share button
- the **sidenav** is the authored navigation tree; `Order line administration` points into
  the `order-admin` module mounted at `back-office`
- the **footer** carries the version button
- **All orders** and **Order lines** are ENTITY routes, rendered from the `Order` and
  `Order Line` descriptors the hosting application registers

Nothing on this page is compiled in. Edit the definition in the neighbouring form, save, and
navigate again — the shell reads the new routes on the next navigation.
