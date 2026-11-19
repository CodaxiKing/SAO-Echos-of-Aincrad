package com.aincrad.effects.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** O endereço de download do pack, sem servidor. */
class PackAddressTest {

    @Test
    void oEnderecoDaConfigVale() {
        assertEquals("http://1.2.3.4:8163", PackAddress.base("http://1.2.3.4:8163/", "jogar.exemplo.com", "", 8163));
        assertEquals("https://cdn.exemplo.com/packs", PackAddress.base(" https://cdn.exemplo.com/packs ", null, "", 8163));
    }

    @Test
    void semConfigValeOEnderecoQueOJogadorUsou() {
        assertEquals("http://jogar.exemplo.com:8163", PackAddress.base("", "Jogar.Exemplo.com.", "", 8163));
        assertEquals("http://203.0.113.7:8163", PackAddress.base("", "203.0.113.7", "10.0.0.5", 8163));
        // Forge e alguns proxies põem dados depois de um \0 no host.
        assertEquals("http://jogar.exemplo.com:8163", PackAddress.base("", "jogar.exemplo.com\0FML3\0", "", 8163));
        assertEquals("http://[2001:db8::1]:8163", PackAddress.base("", "2001:db8::1", "", 8163));
    }

    @Test
    void semNadaCaiNoServerIpEDepoisEmLocalhost() {
        assertEquals("http://10.0.0.5:8163", PackAddress.base("", null, "10.0.0.5", 8163));
        assertEquals("http://localhost:8163", PackAddress.base("", "", "", 8163));
        // Host com caracteres estranhos não entra na URL.
        assertEquals("http://localhost:8163", PackAddress.base("", "exemplo.com/x?y", "", 8163));
        assertEquals("http://a.b:1/aincrad-pack.zip?h=abc", PackAddress.url("http://a.b:1", "aincrad-pack.zip", "abc"));
    }
}
