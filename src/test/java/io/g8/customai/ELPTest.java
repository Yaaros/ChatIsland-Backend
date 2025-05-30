package io.g8.customai;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ELPTest {
    // TextParser解析器可以解析HTML、markdown和txt
    // 如果需要解析任意格式使用tika解析器
    @Test
    public void test01(){
        Document document1 =
                ClassPathDocumentLoader
                .loadDocument("rag-test/test.txt",
                               new TextDocumentParser());
        System.out.println(document1.text());
    }
    // TextSplitter分割器可以将读到的文件分割成多个token
    // 所有的解析器都具有两个参数，第一个参数是每个段的最大字数，第二个参数是自然语言的最大重叠字数
    /*
        第一个参数过大，会降低查询效率，提高内存占用。如果过大，甚至会超出模型的限制
        第一个参数过小，会把大量的句子造成大量的断开，影响模型理解
        第二个参数的优先级不如第一个，且不得大于20，不得小于1
    */
    @Test
    public void test02(){
        Document document1 =
                ClassPathDocumentLoader
                        .loadDocument("rag-test/test.txt",
                                       new TextDocumentParser());
        DocumentSplitter splitter = new DocumentByParagraphSplitter(100, 10);
        System.out.println(splitter.split(document1).toString());
    }
    @Test
    public void test03(){
        Document document1 =
                ClassPathDocumentLoader
                        .loadDocument("rag-test/test.txt",
                                       new TextDocumentParser());
        DocumentSplitter splitter = new DocumentByParagraphSplitter(100, 10);
        List<TextSegment> segments = splitter.split(document1);
        QwenEmbeddingModel em = QwenEmbeddingModel.builder().apiKey(System.getenv("DASHSCOPE_KEY")).build();
        Response<List<Embedding>> vectors = em.embedAll(segments);
        System.out.println(vectors);
    }

    // java.lang.NullPointerException:
    // Cannot invoke "dev.langchain4j.data.segment.TextSegment.text()"
    // because the return value of "dev.langchain4j.store.embedding.EmbeddingMatch.embedded()" is null
    @Test
    public void test04(){
        Document document1 =
                ClassPathDocumentLoader
                        .loadDocument("rag-test/test.txt",
                                new TextDocumentParser());
        DocumentSplitter splitter = new DocumentByParagraphSplitter(100, 10);
        List<TextSegment> segments = splitter.split(document1);
        QwenEmbeddingModel em = QwenEmbeddingModel.builder().apiKey(System.getenv("DASHSCOPE_KEY")).build();
        List<Embedding> vectors = em.embedAll(segments).content();
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.addAll(vectors,segments);
        Response<Embedding> question = em.embed("招募有几种?");
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(question.content())
                .maxResults(3)
                .build();
        EmbeddingSearchResult<TextSegment> results = store.search(request);
        results.matches().forEach(result->
                System.out.println(
                        result.embedded().text()+
                        "的分数为:"+
                        result.score()));

    }
}
