package io.g8.customai.chat.controller;

import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModelReply;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.TokenStream;
import io.g8.customai.chat.config.AiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    @Autowired
    private QwenStreamingChatModel qsc;
    @PostMapping(value = "/stream",
                 produces = "text/event-stream;charset=UTF-8")
    public Flux<String> stream(@RequestBody(required = false)Map<String, Object> input) {
        final String todo = (input == null|| input.isEmpty())
                            ?"你是谁"
                            :input.get("msg").toString();
        return Flux.create(sink -> {
            qsc.chat(todo, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    sink.next(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse chatResponse) {
                    sink.complete();
                }

                @Override
                public void onError(Throwable throwable) {
                     sink.error(throwable);
                }
            });
        });
    }
    @Autowired
    public AiConfig.AiAssistant assistant;
    @PostMapping(value = "/user-stream",
                 produces = "text/event-stream;charset=UTF-8")
    public Flux<String> memoryStream(@RequestBody(required = false)Map<String, Object> input) {
        final String todo = (input == null|| input.isEmpty())
                ?"你是谁"
                :input.get("msg").toString();
        TokenStream tokenStream = assistant.stream(todo);

        return Flux.create(sink -> {
            tokenStream.onPartialResponse(sink::next)
                       .onCompleteResponse(c->sink.complete())
                       .onError(sink::error)
                       .start();


        });
    }
}
