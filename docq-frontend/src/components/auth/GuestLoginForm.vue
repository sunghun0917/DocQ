<script setup lang="ts">
import { ref } from 'vue';
import { useUserStore } from '@/stores/userStore';
import { getErrorMessage } from '@/services/api';

const emit = defineEmits(['success', 'close', 'error']);

const userStore = useUserStore();
const nickname = ref('');
const isChecked = ref(false);
const checkMsg = ref('');
const checkStatus = ref<'success' | 'error' | ''>('');

const confirmNickname = async () => {
    if (nickname.value.trim().length < 2) {
        checkMsg.value = "닉네임은 두 글자 이상이어야 해요!";
        checkStatus.value = 'error';
        return;
    }
    if (!isChecked.value) {
        checkMsg.value = "닉네임 중복 확인을 해주세요!";
        checkStatus.value = 'error';
        return;
    }
    
    // 게스트 전용 로직이라도 닉네임 설정은 loginWithApi를 씀 (MainView 로직 참조)
    // 기존 MainView에서는 GuestLogin 버튼 누르면 자동으로 randomId 생성해서 로그인했음.
    // 하지만 "게스트 로그인 -> 닉네임 입력 -> start" 흐름으로 변경되었으므로
    // 여기서 randomId를 생성하는 게 아니라, 닉네임만 입력받아 로그인(혹은 게스트 등록)을 처리해야 함.
    // 기존 MainView의 confirmNickname 로직을 그대로 가져옴.
    
    // 만약 "게스트"가 닉네임을 짓는 것이라면, API가 loginWithApi(nickname) 형태이므로 그대로 사용.
    const result = await userStore.loginAsGuest(nickname.value.trim());
    if (!result.success) {
        checkMsg.value = getErrorMessage((result as any).error);
        checkStatus.value = 'error';
        return;
    }
    emit('success');
};

const onNicknameInput = () => {
    if (nickname.value.length > 10) {
        nickname.value = nickname.value.slice(0, 10);
    }
    isChecked.value = false;
};


const checkNickname = async () => {
    if (nickname.value.trim().length < 2) {
        checkMsg.value = "닉네임은 두 글자 이상이어야 해요!";
        checkStatus.value = 'error';
        return;
    }
    const { isAvailable, message } = await userStore.checkNickname(nickname.value.trim());
    checkMsg.value = message;
    checkStatus.value = isAvailable ? 'success' : 'error';
    isChecked.value = isAvailable;
};
</script>

<template>
    <div class="auth-form-container">
        <!-- Close Button -->
        <button class="close-btn" @click="$emit('close')">
            <span class="material-symbols-outlined">close</span>
        </button>

        <div class="input-area">
            <div class="input-group mb-4">
                <div class="input-row">
                    <div class="input-wrapper flex-grow">
                        <input 
                            type="text" 
                            v-model="nickname" 
                            placeholder="닉네임을 입력하세요" 
                            @keyup.enter="confirmNickname" 
                            @input="onNicknameInput"
                            maxlength="10"
                            autofocus 
                        />
                    </div>
                    <button class="cartoon-btn secondary small-btn" @click="checkNickname">확인</button>
                </div>
                <p v-if="checkMsg" class="check-msg" :class="checkStatus">{{ checkMsg }}</p>
            </div>
            <button @click="confirmNickname" class="cartoon-btn primary large-btn">
                <span>시작하기</span>
                <span class="material-symbols-outlined">play_arrow</span>
            </button>
        </div>
    </div>
</template>

<style scoped>
/* 공통 스타일 복사 */
.mb-4 { margin-bottom: 1rem; }
.cartoon-btn {
    width: 100%; padding: 12px 20px; border: none; border-radius: 16px;
    font-family: 'Jua', sans-serif; font-size: 1.4rem; color: white;
    cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 8px;
    transition: all 0.1s ease; position: relative; top: 0;
}
.cartoon-btn.primary { background: #7cb342; box-shadow: 0 5px 0 #558b2f; }
.cartoon-btn.primary:hover { background: #8bc34a; }
.cartoon-btn.primary:active { top: 5px; box-shadow: 0 0 0 #558b2f; }

.input-area { display: flex; flex-direction: column; gap: 12px; }
.input-wrapper { position: relative; border: 3px solid #d7ccc8; border-radius: 12px; background: #fff; transition: all 0.3s; }
.input-wrapper:focus-within { border-color: var(--leaf-green); transform: scale(1.02); }
.input-group { display: flex; flex-direction: column; gap: 4px; }
.input-row { display: flex; gap: 8px; align-items: center; }
.flex-grow { flex-grow: 1; }

.cartoon-btn.secondary { background: #ff7043; box-shadow: 0 5px 0 #d84315; }
.cartoon-btn.secondary:hover { background: #ff8a65; }
.cartoon-btn.secondary:active { top: 5px; box-shadow: 0 0 0 #d84315; }

.cartoon-btn.small-btn {
  width: auto;
  padding: 8px 12px;
  font-size: 1rem;
  white-space: nowrap;
}

.check-msg { font-size: 0.9rem; margin-left: 4px; }
.check-msg.success { color: var(--leaf-dark); }
.check-msg.error { color: #d32f2f; }

.input-area input { width: 100%; border: none; background: transparent; padding: 10px; font-size: 1.3rem; text-align: center; color: var(--text-brown); font-family: 'Jua', sans-serif; outline: none; }
.input-area input::placeholder { color: #bcaaa4; font-size: 1.1rem; }

.close-btn {
    position: absolute; top: 10px; right: 10px;
    width: 32px; height: 32px;
    background: var(--leaf-green); border: none; border-radius: 50%;
    color: white; display: flex; align-items: center; justify-content: center;
    cursor: pointer; box-shadow: 0 2px 5px rgba(0,0,0,0.2);
    transition: all 0.2s; z-index: 10;
}
.close-btn:hover { transform: scale(1.1); background: var(--leaf-dark); }
</style>
