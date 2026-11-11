package app.cuepipe;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;

public final class SlotsTest {
    @Test
    public void fourLanguagesFillFourSlots() {
        List<Shelf.Track> tracks = List.of(
                new Shelf.Track(1, "en", "manual", "en"),
                new Shelf.Track(2, "de", "manual", "de"),
                new Shelf.Track(3, "ja", "auto", "ja"),
                new Shelf.Track(4, "fr", "manual", "fr"),
                new Shelf.Track(5, "es", "manual", "es"));
        assertArrayEquals(new int[]{0, 1, 2, 3}, MainActivity.defaultSlots(tracks));
    }

    @Test
    public void twoLanguagesLeaveTheLastSlotsEmpty() {
        List<Shelf.Track> tracks = List.of(
                new Shelf.Track(1, "en", "manual", "en"),
                new Shelf.Track(2, "de", "manual", "de"));
        assertArrayEquals(new int[]{0, 1, -1, -1}, MainActivity.defaultSlots(tracks));
    }
}