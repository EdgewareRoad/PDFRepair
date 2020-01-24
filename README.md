# PDFRepair

This tool isn't a generic repair for all known conditions of PDFs but does solve some common problems that we've seen in the field. Some older PDF generators (some dating from prior to ISO 32000-1:2008) do seem to produce some PDF documents that, although valid at the document level, at the page level, contain non-conforming data streams.

A particular known problem is that of text position elements where one or both of the numeric position values are intended to be negative but, rather than using a single minus sign, a double minus has been used.

e.g. the line

> `13.4 -654.2 TD`

would be represented as

> `13.4 --654.2 TD`

This minor issue is spotted and corrected by some software (e.g. Adobe's own tools) but is an issue for some others (e.g. the PDF reader within Microsoft's Edge Web browser at the time of writing).

For consistency's sake and to ensure maximum compliance, this tool opens PDFs and parses the page content, renormalising it so that it should remove abnormalities like the above.

It relies upon the excellent PDFBox Java library from 
Apache.