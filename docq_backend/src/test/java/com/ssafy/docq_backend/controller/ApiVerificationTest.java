package com.ssafy.docq_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.docq_backend.domain.room.dto.RoomDto;
import com.ssafy.docq_backend.domain.user.dto.UserDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ApiVerificationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        @DisplayName("유저 로그인 성공")
        void loginSuccess() throws Exception {
                UserDto.UserLoginRequest request = new UserDto.UserLoginRequest("testUser");

                mockMvc.perform(post("/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.userId").exists())
                                .andExpect(jsonPath("$.nickname").value("testUser"));
        }

        @Test
        @DisplayName("유저 로그인 실패 - 중복 닉네임")
        void loginFailDuplicate() throws Exception {
                // 1. 첫 번째 로그인 (성공)
                UserDto.UserLoginRequest request1 = new UserDto.UserLoginRequest("duplicateUser");
                mockMvc.perform(post("/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request1)))
                                .andExpect(status().isOk());

                // 2. 두 번째 로그인 (실패 - U002 Conflict)
                mockMvc.perform(post("/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request1)))
                                .andDo(print())
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("U002"));
        }

        @Test
        @DisplayName("방 생성 성공")
        void createRoomSuccess() throws Exception {
                // 1. 유저 생성
                UserDto.UserLoginRequest loginRequest = new UserDto.UserLoginRequest("hostUser");
                MvcResult loginResult = mockMvc.perform(post("/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                String responseBody = loginResult.getResponse().getContentAsString();
                UserDto.UserLoginResponse loginResponse = objectMapper.readValue(responseBody,
                                UserDto.UserLoginResponse.class);
                String userId = loginResponse.getUserId();

                // 2. 방 생성 요청
                RoomDto.RoomCreateRequest roomRequest = new RoomDto.RoomCreateRequest(
                                userId, 4, "Test Room", false, null);

                mockMvc.perform(post("/rooms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(roomRequest)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.roomId").exists())
                                .andExpect(jsonPath("$.inviteCode").exists())
                                .andExpect(jsonPath("$.hostId").value(userId));
        }

        @Test
        @DisplayName("방 생성 실패 - 존재하지 않는 유저 (U001)")
        void createRoomFailInvalidHost() throws Exception {
                RoomDto.RoomCreateRequest roomRequest = new RoomDto.RoomCreateRequest(
                                "invalid-uuid", 4, "Test Room", false, null);

                mockMvc.perform(post("/rooms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(roomRequest)))
                                .andDo(print())
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("U001"));
        }

        @Test
        @DisplayName("방 생성 실패 - 비공개 방 비밀번호 누락 (C002)")
        void createRoomFailPrivateNoPassword() throws Exception {
                // 1. 유저 생성
                UserDto.UserLoginRequest loginRequest = new UserDto.UserLoginRequest("privateHost");
                MvcResult loginResult = mockMvc.perform(post("/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn();
                String userId = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("userId")
                                .asText();

                // 2. 비밀번호 없는 비공개 방 생성
                RoomDto.RoomCreateRequest roomRequest = new RoomDto.RoomCreateRequest(
                                userId, 4, "Private Room", true, "");

                mockMvc.perform(post("/rooms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(roomRequest)))
                                .andDo(print())
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("C002"));
        }

        @Test
        @DisplayName("방 조회 성공 - RoomId")
        void getRoomByIdSuccess() throws Exception {
                // 1. 유저 및 방 생성
                UserDto.UserLoginRequest loginRequest = new UserDto.UserLoginRequest("finderUser");
                String userId = objectMapper.readTree(mockMvc.perform(post("/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn().getResponse().getContentAsString()).get("userId").asText();

                RoomDto.RoomCreateRequest roomRequest = new RoomDto.RoomCreateRequest(
                                userId, 4, "Find Me", false, null);

                String roomResponseBody = mockMvc.perform(post("/rooms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(roomRequest)))
                                .andReturn().getResponse().getContentAsString();
                String roomId = objectMapper.readTree(roomResponseBody).get("roomId").asText();

                // 2. 조회
                mockMvc.perform(get("/rooms/" + roomId))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.roomId").value(roomId))
                                .andExpect(jsonPath("$.title").value("Find Me"))
                                .andExpect(jsonPath("$.status").value("WAITING"));
        }

        @Test
        @DisplayName("방 조회 성공 - InviteCode")
        void getRoomByInviteCodeSuccess() throws Exception {
                // 1. 유저 및 방 생성
                UserDto.UserLoginRequest loginRequest = new UserDto.UserLoginRequest("inviteUser");
                String userId = objectMapper.readTree(mockMvc.perform(post("/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn().getResponse().getContentAsString()).get("userId").asText();

                RoomDto.RoomCreateRequest roomRequest = new RoomDto.RoomCreateRequest(
                                userId, 4, "Invite Me", false, null);

                String roomResponseBody = mockMvc.perform(post("/rooms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(roomRequest)))
                                .andReturn().getResponse().getContentAsString();
                String inviteCode = objectMapper.readTree(roomResponseBody).get("inviteCode").asText();

                // 2. 조회
                mockMvc.perform(get("/rooms?inviteCode=" + inviteCode))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.inviteCode").doesNotExist()) // Response에 inviteCode는 없음 (명세 확인
                                                                                    // 필요, DTO에는 없음) -> 아 DTO 확인해보니
                                                                                    // Response에 inviteCode 없음.
                                .andExpect(jsonPath("$.roomId").exists())
                                .andExpect(jsonPath("$.title").value("Invite Me"));
        }

        @Test
        @DisplayName("방 조회 실패 - 존재하지 않는 방 (R001)")
        void getRoomFailNotFound() throws Exception {
                mockMvc.perform(get("/rooms/invalid-room-id"))
                                .andDo(print())
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("R001"));

        }

        @Test
        @DisplayName("방 입장 검증 성공 - 공개방")
        void verifyJoinSuccessPublic() throws Exception {
                // 1. 방 생성 (공개)
                String userId = createUser("joinerHost");
                String roomId = createRoom(userId, "Public Room", false, null);

                // 2. 입장 검증
                RoomDto.RoomJoinRequest joinRequest = new RoomDto.RoomJoinRequest(roomId, null);

                mockMvc.perform(post("/rooms/join")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(joinRequest)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.roomId").value(roomId))
                                .andExpect(jsonPath("$.roomCode").exists())
                                .andExpect(jsonPath("$.status").value("WAITING"));
        }

        @Test
        @DisplayName("방 입장 검증 성공 - 비공개방")
        void verifyJoinSuccessPrivate() throws Exception {
                // 1. 방 생성 (비공개)
                String userId = createUser("privateJoinerHost");
                String roomId = createRoom(userId, "Private Room", true, "1234");

                // 2. 입장 검증
                RoomDto.RoomJoinRequest joinRequest = new RoomDto.RoomJoinRequest(roomId, "1234");

                mockMvc.perform(post("/rooms/join")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(joinRequest)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.roomId").value(roomId))
                                .andExpect(jsonPath("$.roomCode").exists())
                                .andExpect(jsonPath("$.status").value("WAITING"));
        }

        @Test
        @DisplayName("방 입장 검증 실패 - 비밀번호 불일치 (R005)")
        void verifyJoinFailWrongPassword() throws Exception {
                String userId = createUser("wrongPwHost");
                String roomId = createRoom(userId, "Private Room", true, "1234");

                RoomDto.RoomJoinRequest joinRequest = new RoomDto.RoomJoinRequest(roomId, "0000");

                mockMvc.perform(post("/rooms/join")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(joinRequest)))
                                .andDo(print())
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.code").value("R005"));
        }

        // Helper Methods
        private String createUser(String nickname) throws Exception {
                UserDto.UserLoginRequest loginRequest = new UserDto.UserLoginRequest(nickname);
                String response = mockMvc.perform(post("/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn().getResponse().getContentAsString();
                return objectMapper.readTree(response).get("userId").asText();
        }

        private String createRoom(String userId, String title, boolean isPrivate, String password) throws Exception {
                RoomDto.RoomCreateRequest roomRequest = new RoomDto.RoomCreateRequest(
                                userId, 4, title, isPrivate, password);
                String response = mockMvc.perform(post("/rooms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(roomRequest)))
                                .andReturn().getResponse().getContentAsString();
                return objectMapper.readTree(response).get("roomId").asText();
        }
}
