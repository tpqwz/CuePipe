package app.cuepipe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
}
