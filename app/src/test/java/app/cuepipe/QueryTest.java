package app.cuepipe;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class QueryTest {
    @Test
    public void operatorsStayUppercase() {
        assertEquals("alpha OR beta", Query.match("alpha OR beta", false, false));
        assertEquals("alpha NOT beta", Query.match("alpha NOT beta", false, false));
    }

    @Test
    public void phraseAndPrefixPassThrough() {
        assertEquals("\"hello world\"", Query.match("\"hello world\"", false, false));
        assertEquals("eleph*", Query.match("eleph*", false, false));
    }

    @Test
    public void fuzzyIncludesAOneEditNeighbor() {
        String match = Query.match("helo~", true, false);
        assertTrue(match.contains("hello"));
        assertTrue(match.startsWith("("));
    }

    @Test
    public void conceptsOrTheSynonym() {
        assertEquals("(movie OR film OR picture)", Query.match("movie", false, true));
    }

    @Test
    public void aHitMarksTheWordAndNotALongerOne() {
        List<Query.Mark> marks = Query.marks("category cat", "cat", false);
        assertEquals(1, marks.size());
        assertEquals("cat", "category cat".substring(marks.get(0).start, marks.get(0).end));
        assertFalse(marks.get(0).related);
    }

    @Test
    public void aShortEndingAndAConceptAreMarked() {
        List<Query.Mark> films = Query.marks("two films", "film", false);
        assertEquals("films", "two films".substring(films.get(0).start, films.get(0).end));
        List<Query.Mark> related = Query.marks("a great film", "movie", true);
        assertEquals(1, related.size());
        assertEquals("film", "a great film".substring(related.get(0).start, related.get(0).end));
        assertTrue(related.get(0).related);
        List<Query.Mark> phrase = Query.marks("the cat sat", "\"cat sat\"", false);
        assertEquals("cat sat", "the cat sat".substring(phrase.get(0).start, phrase.get(0).end));
    }
}
