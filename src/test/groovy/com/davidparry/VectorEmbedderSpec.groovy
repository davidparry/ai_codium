package com.davidparry

import spock.lang.Specification

class VectorEmbedderSpec extends Specification {

    def "Given the runner create vectors "() {
        given:
        VectorEmbedder embedder =  new VectorEmbedder(System.getenv("OPEN_AI"), System.getenv("MONGO_URL"), System.getenv("ORG_ID"))

        when:
        embedder.doVectors()

        then:
        noExceptionThrown()


    }

}
