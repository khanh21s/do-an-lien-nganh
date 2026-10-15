package com.parroto.vocabulary.service.dictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.vocabulary.service.dictionary.FreeDictionaryClient.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FreeDictionaryClientTest {

    static final String HELLO = """
            [{"word":"hello","phonetic":"/həˈləʊ/",
              "phonetics":[{"text":"/həˈləʊ/","audio":""},
                           {"text":"/həˈloʊ/","audio":"https://api.dictionaryapi.dev/media/pronunciations/en/hello-us.mp3"}],
              "meanings":[{"partOfSpeech":"noun","definitions":[{"definition":"\\"Hello!\\" or an equivalent greeting.","example":"She gave me a warm hello."}]},
                          {"partOfSpeech":"interjection","definitions":[{"definition":"A greeting used when answering the telephone."}]}]}]
            """;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private FreeDictionaryClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new FreeDictionaryClient(restTemplate, new ObjectMapper(), "https://dict.test/api/v2/entries/en/", true);
    }

    @Test
    void parsesIpaUsAudioAndDefinitions() {
        server.expect(requestTo("https://dict.test/api/v2/entries/en/hello"))
                .andRespond(withSuccess(HELLO, MediaType.APPLICATION_JSON));
        var lookup = client.lookup("hello");
        assertThat(lookup.outcome()).isEqualTo(Outcome.FOUND);
        var e = lookup.entry();
        assertThat(e.ipa()).isEqualTo("həˈləʊ");
        assertThat(e.audioUrl()).endsWith("hello-us.mp3");
        assertThat(e.definitions()).hasSize(2);
        assertThat(e.definitions().get(0).partOfSpeech()).isEqualTo("noun");
        assertThat(e.definitions().get(0).example()).isEqualTo("She gave me a warm hello.");
        assertThat(e.definitions().get(1).example()).isNull();
    }

    @Test
    void encodesThePathSegment() {
        server.expect(requestTo("https://dict.test/api/v2/entries/en/take%20off"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"title\":\"No Definitions Found\"}"));
        assertThat(client.lookup("take off").outcome()).isEqualTo(Outcome.MISS);
    }

    @Test
    void serverErrorsAndGarbageAreHandled() {
        server.expect(requestTo("https://dict.test/api/v2/entries/en/x")).andRespond(withServerError());
        assertThat(client.lookup("x").outcome()).isEqualTo(Outcome.FAILED);
        server.reset();
        server.expect(requestTo("https://dict.test/api/v2/entries/en/y"))
                .andRespond(withSuccess("not json", MediaType.TEXT_PLAIN));
        assertThat(client.lookup("y").outcome()).isEqualTo(Outcome.MISS);
    }

    @Test
    void disabledNeverCalls() {
        FreeDictionaryClient off = new FreeDictionaryClient(restTemplate, new ObjectMapper(), "https://dict.test", false);
        assertThat(off.lookup("hello").outcome()).isEqualTo(Outcome.DISABLED);
        server.verify();
    }
}
