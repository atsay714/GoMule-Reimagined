This directory is optional and may be empty.

Drop community-curated "duplicate item" lists here to have the Flavie report
cross-reference and flag known duped items:

  - Plain text files (*.txt), one item description per line.
  - Zip archives (*.zip) containing one or more *.txt files in the same format.

GoMule reads every file directly in this directory (not subdirectories) at
report-generation time. If this directory contains no files, the report is
generated normally with no dupe-list cross-referencing -- this directory
existing at all (even empty) is what matters: prior to this file being added,
the directory wasn't tracked in the repository, so a fresh checkout or
distribution build never created it, and generating a Flavie report crashed
with a NullPointerException every time.
