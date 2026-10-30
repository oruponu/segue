# Key notation

Rules for turning `SegueMusicResult.key_note` (0-11, C=0) and `key_scale` (0=major, 1=minor) into a display string.

| key_note | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| major | C | Db | D | Eb | E | F | Gb | G | Ab | A | Bb | B |
| minor | C | C# | D | Eb | E | F | F# | G | G# | A | Bb | B |

- The string is `"<note> Major"` or `"<note> Minor"` (for example `C Minor`, `Ab Major`)
- If key_note is outside 0-11, the string is `Unknown`
