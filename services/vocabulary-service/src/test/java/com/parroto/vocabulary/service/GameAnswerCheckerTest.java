package com.parroto.vocabulary.service;

import com.parroto.vocabulary.entity.GameResult;
import com.parroto.vocabulary.entity.VocabCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GameAnswerCheckerTest {

    private static VocabCard card(String word, String meaning) {
        return VocabCard.builder().id(UUID.randomUUID()).word(word).meaning(meaning).build();
    }

    @Test
    void exactAnswerIgnoresCaseSpacesPunctuationAndLeadingTo() {
        VocabCard c = card("decide", "quyết định");
        assertThat(GameAnswerChecker.check("  Decide. ", c, List.of()).result()).isEqualTo(GameResult.CORRECT);
        assertThat(GameAnswerChecker.check("to decide", c, List.of()).result()).isEqualTo(GameResult.CORRECT);
        VocabCard phrase = card("to look forward to (doing sth)", "mong đợi");
        assertThat(GameAnswerChecker.check("look forward to", phrase, List.of()).result()).isEqualTo(GameResult.CORRECT);
    }

    @Test
    void alternativeSpellingsAreAccepted() {
        VocabCard c = card("colour/color", "màu sắc");
        assertThat(GameAnswerChecker.check("color", c, List.of()).result()).isEqualTo(GameResult.CORRECT);
        assertThat(GameAnswerChecker.check("colour", c, List.of()).result()).isEqualTo(GameResult.CORRECT);
    }

    @Test
    void anotherWordWithTheSameMeaningIsCorrect() {
        VocabCard q = card("enormous", "to lớn, khổng lồ");
        VocabCard huge = card("huge", "khổng lồ");
        VocabCard unrelated = card("tiny", "nhỏ xíu");
        GameAnswerChecker.Check r = GameAnswerChecker.check("Huge", q, List.of(huge, unrelated));
        assertThat(r.result()).isEqualTo(GameResult.CORRECT);
        assertThat(r.acceptedWord()).isEqualTo("huge");
        assertThat(GameAnswerChecker.check("tiny", q, List.of(unrelated)).result()).isEqualTo(GameResult.WRONG);
    }

    @Test
    void oneTypoIsNearOnlyForLongerWords() {
        VocabCard env = card("environment", "môi trường");
        assertThat(GameAnswerChecker.check("enviroment", env, List.of()).result()).isEqualTo(GameResult.NEAR);
        VocabCard receive = card("receive", "nhận");
        assertThat(GameAnswerChecker.check("recieve", receive, List.of()).result()).isEqualTo(GameResult.NEAR);
        assertThat(GameAnswerChecker.check("recive", receive, List.of()).result()).isEqualTo(GameResult.NEAR);
        assertThat(GameAnswerChecker.check("reciev", receive, List.of()).result()).isEqualTo(GameResult.WRONG);
        VocabCard cat = card("cat", "con mèo");
        assertThat(GameAnswerChecker.check("cut", cat, List.of()).result()).isEqualTo(GameResult.WRONG);
        assertThat(GameAnswerChecker.check("", cat, List.of()).result()).isEqualTo(GameResult.WRONG);
    }

    @Test
    void hintsMaskTheWordProgressively() {
        assertThat(GameAnswerChecker.mask("huge", 1, false)).isEqualTo("_ _ _ _");
        assertThat(GameAnswerChecker.mask("huge", 2, false)).isEqualTo("h _ _ _");
        assertThat(GameAnswerChecker.mask("huge", 3, true)).isEqualTo("h _ _ e");
        assertThat(GameAnswerChecker.mask("thank you", 2, false)).isEqualTo("t _ _ _ _    y _ _");
        assertThat(GameAnswerChecker.letterCount("thank you")).isEqualTo(8);
        assertThat(GameAnswerChecker.mask("to decide", 1, false)).isEqualTo("_ _ _ _ _ _");
    }

    @Test
    void exampleIsBlankedIncludingInflections() {
        assertThat(GameAnswerChecker.blankedExample("She decided to stay.", "decide")).isEqualTo("She _____ to stay.");
        assertThat(GameAnswerChecker.blankedExample("Huge waves hit the huge ship.", "huge"))
                .isEqualTo("_____ waves hit the _____ ship.");
        assertThat(GameAnswerChecker.blankedExample("Nothing here.", "huge")).isNull();
        assertThat(GameAnswerChecker.blankedExample(null, "huge")).isNull();
    }
}
