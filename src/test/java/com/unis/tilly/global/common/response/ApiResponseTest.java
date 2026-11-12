package com.unis.tilly.global.common.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 성공_응답에는_errorCode_필드가_생략된다() throws Exception {
        ApiResponse<Map<String, Object>> response = ApiResponse.success("할 일이 추가되었습니다.", Map.of("id", 103));

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.has("errorCode")).isFalse();
        assertThat(json.get("message").asText()).isEqualTo("할 일이 추가되었습니다.");
        assertThat(json.get("data").get("id").asInt()).isEqualTo(103);
    }

    @Test
    void 데이터_없는_성공_응답은_data가_null이다() throws Exception {
        ApiResponse<Void> response = ApiResponse.success("로그아웃되었습니다.");

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("data").isNull()).isTrue();
        assertThat(json.has("errorCode")).isFalse();
    }

    @Test
    void 실패_응답은_success_false와_errorCode_이름을_포함한다() throws Exception {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.TASK_NOT_FOUND);

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("errorCode").asText()).isEqualTo("TASK_NOT_FOUND");
        assertThat(json.get("message").asText()).isEqualTo(ErrorCode.TASK_NOT_FOUND.getMessage());
    }

    @Test
    void 실패_응답은_메시지를_직접_지정할_수_있다() {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.BAD_REQUEST, "닉네임은 2~10자여야 합니다.");

        assertThat(response.getMessage()).isEqualTo("닉네임은 2~10자여야 합니다.");
        assertThat(response.getErrorCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void JSON_필드_순서는_success_errorCode_message_data이다() throws Exception {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.BAD_REQUEST);

        String json = objectMapper.writeValueAsString(response);

        int successIndex = json.indexOf("success");
        int errorCodeIndex = json.indexOf("errorCode");
        int messageIndex = json.indexOf("message");
        int dataIndex = json.indexOf("data");

        assertThat(successIndex).isLessThan(errorCodeIndex);
        assertThat(errorCodeIndex).isLessThan(messageIndex);
        assertThat(messageIndex).isLessThan(dataIndex);
    }
}
