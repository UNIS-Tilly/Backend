package com.unis.tilly.ai.prompt;

// resources/prompts/ 아래 파일 이름의 앞부분.
// 종류마다 {name}-system.txt(지시문), {name}-user.txt(사용자 데이터 틀), {name}.schema.json(응답 JSON 스키마)
public enum PromptType {
    QUESTION("question"),
    WEEKLY_INSIGHT("weekly-insight");

    private final String fileName;

    PromptType(String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }
}
