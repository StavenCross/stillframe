# HIDIVE discovery row

Highlight HIDIVE on the dock. When there are no readable Watch Next titles
for that service, **Popular on HIDIVE** shows three different shows from a
small, curated catalog. Stillframe shuffles the catalog when its launcher
activity is created and keeps the selection stable while you browse. If
HIDIVE supplies Watch Next titles, those take precedence.

The picks use public TMDB poster URLs and the existing bounded artwork cache.
They do not require a TMDB API token or a HIDIVE account connection. Selecting
a poster opens the show's HIDIVE page in the TV app if the app accepts the
link. Availability can vary by region. The catalog is maintained in
`HidivePicks.java`. Run `./test.sh` to verify selection and fallback behavior.
