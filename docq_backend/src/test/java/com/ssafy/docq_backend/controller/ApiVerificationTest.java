package com.ssafy.docq_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.docq_backend.domain.room.dto.RoomDto;
import com.ssafy.docq_backend.domain.guest.dto.GuestDto;

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
        @DisplayName("게스트 로그인 성공")
        void loginSuccess() throws Exception {
                GuestDto.GuestLoginRequest request = new GuestDto.GuestLoginRequest("testUser");

                mockMvc.perform(post("/guests/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.playerId").exists())
                                .andExpect(jsonPath("$.nickname").value("testUser"));
        }

        @Test
        @DisplayName("게스트 로그인 실패 - 중복 닉네임")
        void loginFailDuplicate() throws Exception {
                // 1. 첫 번째 로그인 (성공)
                GuestDto.GuestLoginRequest request1 = new GuestDto.GuestLoginRequest("duplicateUser");
                mockMvc.perform(post("/guests/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request1)))
                                .andExpect(status().isOk());

                // 2. 두 번째 로그인 (실패 - U002 Conflict)
                mockMvc.perform(post("/guests/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request1)))
                                .andDo(print())
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("U002"));
        }

        @Test
        @DisplayName("방 생성 성공")
        void createRoomSuccess() throws Exception {
                // 1. 게스트 로그인 (playerId 발급)
                GuestDto.GuestLoginRequest loginRequest = new GuestDto.GuestLoginRequest("hostUser");
                MvcResult loginResult = mockMvc.perform(post("/guests/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                String responseBody = loginResult.getResponse().getContentAsString();
                GuestDto.GuestLoginResponse loginResponse = objectMapper.readValue(responseBody,
                                GuestDto.GuestLoginResponse.class);
                String playerId = loginResponse.getPlayerId();

                // 2. 방 생성 요청
                RoomDto.RoomCreateRequest roomRequest = new RoomDto.RoomCreateRequest(
                                playerId, 4, "Test Room", false, null);

                mockMvc.perform(post("/rooms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(roomRequest)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.roomId").exists())
                                .andExpect(jsonPath("$.inviteCode").exists())
                                .andExpect(jsonPath("$.hostPlayerId").value(playerId));
        }

        @Test
        @DisplayName("방 생성 실패 - 존재하지 않는 playerId (U001)")
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
                // 1. 게스트 로그인
                GuestDto.GuestLoginRequest loginRequest = new GuestDto.GuestLoginRequest("privateHost");
                MvcResult loginResult = mockMvc.perform(post("/guests/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn();
                String playerId = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("playerId")
                                .asText();

                // 2. 비밀번호 없는 비공개 방 생성
                RoomDto.RoomCreateRequest roomRequest = new RoomDto.RoomCreateRequest(
                                playerId, 4, "Private Room", true, "");

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
                // 1. 게스트 로그인 및 방 생성
                GuestDto.GuestLoginRequest loginRequest = new GuestDto.GuestLoginRequest("finderUser");
                String playerId = objectMapper.readTree(mockMvc.perform(post("/guests/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn().getResponse().getContentAsString()).get("playerId").asText();

                RoomDto.RoomCreateRequest roomRequest = new RoomDto.RoomCreateRequest(
                                playerId, 4, "Find Me", false, null);

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
                // 1. 게스트 로그인 및 방 생성
                GuestDto.GuestLoginRequest loginRequest = new GuestDto.GuestLoginRequest("inviteUser");
                String playerId = objectMapper.readTree(mockMvc.perform(post("/guests/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn().getResponse().getContentAsString()).get("playerId").asText();

                RoomDto.RoomCreateRequest roomRequest = new RoomDto.RoomCreateRequest(
                                playerId, 4, "Invite Me", false, null);

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
                String playerId = createGuest("joinerHost");
                String roomId = createRoom(playerId, "Public Room", false, null);

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
                String playerId = createGuest("privateJoinerHost");
                String roomId = createRoom(playerId, "Private Room", true, "1234");

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
                String playerId = createGuest("wrongPwHost");
                String roomId = createRoom(playerId, "Private Room", true, "1234");

                RoomDto.RoomJoinRequest joinRequest = new RoomDto.RoomJoinRequest(roomId, "0000");

                mockMvc.perform(post("/rooms/join")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(joinRequest)))
                                .andDo(print())
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.code").value("R005"));
        }

        // Helper Methods
        private String createGuest(String nickname) throws Exception {
                GuestDto.GuestLoginRequest loginRequest = new GuestDto.GuestLoginRequest(nickname);
                String response = mockMvc.perform(post("/guests/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn().getResponse().getContentAsString();
                return objectMapper.readTree(response).get("playerId").asText();
        }

        private String createRoom(String playerId, String title, boolean isPrivate, String password) throws Exception {
                RoomDto.RoomCreateRequest roomRequest = new RoomDto.RoomCreateRequest(
                                playerId, 4, title, isPrivate, password);
                String response = mockMvc.perform(post("/rooms")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(roomRequest)))
                                .andReturn().getResponse().getContentAsString();
                return objectMapper.readTree(response).get("roomId").asText();
        }
}
