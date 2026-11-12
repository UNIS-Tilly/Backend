package com.unis.tilly.global.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 시큐리티 필터는 이 테스트의 관심사가 아니므로(인증 자체는 auth-core의 다른 테스트가 다룬다) 끈다.
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    // Boot 4의 @WebMvcTest 슬라이스는 Jackson ObjectMapper 빈을 자동 주입하지 않아 직접 생성한다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void CustomException은_ErrorCode의_상태코드와_errorCode_이름으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/custom-exception"))
                .andExpect(status().is(ErrorCode.TASK_NOT_FOUND.getStatus()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(ErrorCode.TASK_NOT_FOUND.getMessage()));
    }

    @Test
    void Valid_검증_실패는_400과_필드_메시지를_fail_message로_반환한다() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new TestRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void 잘못된_요청_본문은_400_BAD_REQUEST로_처리된다() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType("application/json")
                        .content("{ 이건 JSON이 아님"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void 존재하지_않는_경로는_500이_아니라_404_NOT_FOUND다() throws Exception {
        mockMvc.perform(get("/test/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void 예기치_못한_예외는_500과_일반_메시지만_반환하고_내부정보를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/custom-exception")
        public ApiResponse<Void> throwCustomException() {
            throw new CustomException(ErrorCode.TASK_NOT_FOUND);
        }

        @PostMapping("/valid")
        public ApiResponse<Void> valid(@RequestBody @Valid TestRequest request) {
            return ApiResponse.success("ok");
        }

        @GetMapping("/unexpected")
        public ApiResponse<Void> unexpected() {
            throw new RuntimeException("내부 구현 정보: 절대 노출되면 안 됨");
        }
    }

    record TestRequest(@NotBlank(message = "제목은 비워둘 수 없습니다.") String title) {
    }
}
