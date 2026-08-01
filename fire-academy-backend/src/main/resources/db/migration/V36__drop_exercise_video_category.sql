-- The category was a free-text field that did two small things: it fed the search index and it
-- printed under the name. Nothing grouped or filtered by it, and nothing suggested the values
-- already in use — so a library would drift into "nogi", "Nogi" and "nogi/posladki" as three
-- separate labels that cannot find each other. One field fewer at entry; the name carries the
-- whole meaning now.

-- Fold the category into the name rather than dropping the words. It was the visible subtitle and
-- part of what search matched, so deleting it would quietly lose both. Skipped where the name
-- already contains it, to avoid "Przysiad - nogi - nogi".
UPDATE exercise_videos
SET name = LEFT(name || ' - ' || category, 150)
WHERE category IS NOT NULL
  AND btrim(category) <> ''
  AND position(lower(btrim(category)) in lower(name)) = 0;

ALTER TABLE exercise_videos DROP COLUMN category;

-- search_text is maintained by the application (lowercased, diacritics stripped). Rebuild it here
-- for the rows just rewritten; translate() mirrors what ExerciseVideo.buildSearchText does in Java.
UPDATE exercise_videos
SET search_text = btrim(lower(translate(
    name,
    'ąćęłńóśźżĄĆĘŁŃÓŚŹŻ',
    'acelnoszzACELNOSZZ'
)));
