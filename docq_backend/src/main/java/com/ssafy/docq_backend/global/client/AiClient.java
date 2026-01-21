package com.ssafy.docq_backend.global.client;

import com.ssafy.docq_backend.domain.game.dto.QuizDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final RestTemplate restTemplate;

    @Value("${ai.url:http://localhost:8000}")
    private String aiUrl;

    public List<QuizDto.Response> generateQuiz(MultipartFile pdfFile) {
        String endpoint = aiUrl + "/ai/upload/pdf";
        log.info("Calling AI Service: {}", endpoint);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(pdfFile.getBytes()) {
                @Override
                public String getFilename() {
                    return pdfFile.getOriginalFilename();
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // AI 서버 응답 구조: { "quizzes": [ ... ], ... }
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<>() {
                    });

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("quizzes")) {
                log.error("Invalid response from AI Service: {}", responseBody);
                return Collections.emptyList();
            }

            // ObjectMapper 등으로 변환하는 것이 좋지만, 여기서는 간단히 캐스팅/매핑
            List<Map<String, Object>> rawQuizzes = (List<Map<String, Object>>) responseBody.get("quizzes");

            return rawQuizzes.stream().map(q -> QuizDto.Response.builder()
                    .question((String) q.get("question"))
                    .options((List<String>) q.get("options"))
                    .answerIndex((Integer) q.get("answerIndex"))
                    .answerText((String) q.get("answerText"))
                    .build()).toList();

        } catch (IOException e) {
            log.error("Failed to read PDF file", e);
            throw new RuntimeException("Failed to process PDF file", e);
        } catch (Exception e) {
            log.error("AI Service Call Error", e);
            throw new RuntimeException("AI Service unavailable", e);
        }
    }
}
