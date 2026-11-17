package com.aincrad.social.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlSocialStoreTest {

    @TempDir
    File tmp;

    @Test
    void gravaELeDeVolta() {
        File file = new File(tmp, "dados.yml");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        YamlSocialStore s = new YamlSocialStore(file, Logger.getAnonymousLogger());
        s.addFriend(a, b);
        s.addRequest(c, a);
        s.rememberName(a, "Kirito");
        s.rememberName(b, "Asuna");
        InboxMessage m = new InboxMessage(UUID.randomUUID(), b, "Asuna", "Encontro no andar 22: 18h", 123L, false);
        s.setInbox(a, List.of(m));
        assertTrue(s.dirty());
        s.save();
        assertFalse(s.dirty());

        YamlSocialStore r = new YamlSocialStore(file, Logger.getAnonymousLogger());
        r.load();
        assertTrue(r.friends(a).contains(b));
        assertTrue(r.friends(b).contains(a));
        assertTrue(r.requests(a).contains(c));
        assertEquals("Kirito", r.name(a));
        assertEquals(b, r.byName("asuna"));
        assertEquals(List.of(m), r.inbox(a));
        assertFalse(r.dirty());
    }
}
