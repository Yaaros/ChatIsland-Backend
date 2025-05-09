package io.g8.customai.knowledge.net;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class QueryResponse {
    // Getters and setters
    private String answer;
    private List<String> sources;

    public QueryResponse(String answer, List<String> sources) {
        this.answer = answer;
        this.sources = sources;
    }

}