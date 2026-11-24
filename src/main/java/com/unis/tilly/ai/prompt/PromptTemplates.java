package com.unis.tilly.ai.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 프롬프트는 src/main/resources/prompts/ 한 곳에서만 관리한다 (사용자 결정 2026-11-12).
// 문구를 바꿀 때는 그 폴더의 파일만 고치면 된다. 기동 시 전부 읽어서 파일이 없으면 앱이 뜨지 않는다.
//
// 프롬프트 인젝션 방어: 지시문(system)에는 사용자 데이터를 넣지 않는다.
// 사용자 데이터는 user 틀의 {{변수}} 자리에 JSON 문자열로만 들어가고, 치환은 한 번만 한다(값 안의 {{...}}는 다시 치환하지 않음).
@Component
public class PromptTemplates {

    private static final String BASE_PATH = "prompts/";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)}}");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<PromptType, Template> templates = new EnumMap<>(PromptType.class);

    public PromptTemplates() {
        for (PromptType type : PromptType.values()) {
            templates.put(type, new Template(
                    read(type.fileName() + "-system.txt"),
                    read(type.fileName() + "-user.txt"),
                    readSchema(type.fileName() + ".schema.json")));
        }
    }

    public Prompt render(PromptType type, Map<String, String> variables) {
        Template template = templates.get(type);
        return new Prompt(template.system(), fill(template.user(), variables), template.schema());
    }

    private String fill(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables.get(name);
            if (value == null) {
                throw new IllegalArgumentException("프롬프트 변수가 없습니다: " + name);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String read(String fileName) {
        try (InputStream in = new ClassPathResource(BASE_PATH + fileName).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 파일을 읽을 수 없습니다: " + BASE_PATH + fileName, e);
        }
    }

    private JsonNode readSchema(String fileName) {
        try {
            return objectMapper.readTree(read(fileName));
        } catch (IOException e) {
            throw new IllegalStateException("응답 스키마가 올바른 JSON이 아닙니다: " + BASE_PATH + fileName, e);
        }
    }

    private record Template(String system, String user, JsonNode schema) {
    }
}
