# SuperGreenfoot project rules

- Treat `BlueJ-Greenfoot-GREENFOOT-RELEASE-3.9.0/` as a verbatim, read-only upstream source snapshot. Do not edit, delete, rename, reformat, build in place, or generate files anywhere inside it.
- Do SuperGreenfoot development in separate directories outside that snapshot. If a working copy is needed, create it separately and record the upstream version and changes made there.
- Keep `MrCohenLibrary150/` as a reference scenario and library. Prefer separate working copies for experiments or migrations so its current behavior can be compared.
- Before any operation that could write into either reference tree, redirect its output to a separate workspace directory and verify the reference tree remains unchanged.
- The GitHub repository (`supergreenfoot/`) is public. Planning documents (plans, decision logs, assessments, drafts) are private: keep them only in the `plans/` folder outside the repository, never create, copy, or commit them inside the repository, and never link to them from repository files.
