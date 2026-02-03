package com.ssafy.docq_backend.global.client;

import com.ssafy.docq_backend.global.client.dto.AiDto;
import com.ssafy.docq_backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final RestTemplate restTemplate;

    @Value("${ai.url:http://ai-service:8000}")
    private String aiUrl;

    public AiDto.JobResponse requestQuizJob(MultipartFile pdfFile) {
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

            HttpEntity<MultiValueMap<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<AiDto.JobResponse> response =
                    restTemplate.exchange(
                            aiUrl + "/ai/upload/pdf",
                            HttpMethod.POST,
                            request,
                            AiDto.JobResponse.class
                    );

            return response.getBody();

        } catch (Exception e) {
            throw new DocqException(ErrorCode.AI_PROCESSING_ERROR);
        }
    }
}
