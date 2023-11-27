package com.davidparry

import spock.lang.Specification

class AIRunnerSpec extends Specification {

    def "AIRunner search for question"() {
        given:
        AIRunner main =
                new AIRunner(System.getenv("OPEN_AI"), System.getenv("MONGO_URL"), System.getenv("ORG_ID"))
        when:
        ParsedChatResponse response = main.search("Explain what AI is for?")

        then:
        response.json() != null
        response.json().answerFound() == "yes"
    }

}
