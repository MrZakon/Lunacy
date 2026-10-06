package net.lunacy.visuals.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StudioProfilesTest {
 @Test void acceptsPortableNames(){assertEquals("PVP 1",StudioProfiles.safeName(" PVP 1 "));assertEquals("Сакура",StudioProfiles.safeName("Сакура"));}
 @Test void rejectsTraversalAndEmptyNames(){assertThrows(IllegalArgumentException.class,()->StudioProfiles.safeName("../secret"));assertThrows(IllegalArgumentException.class,()->StudioProfiles.safeName(" "));}
}
