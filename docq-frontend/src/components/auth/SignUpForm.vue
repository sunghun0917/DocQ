<script setup lang="ts">
import { ref } from 'vue';
import { useUserStore } from '@/stores/userStore';
import { getErrorMessage } from '@/services/api';

const emit = defineEmits(['success', 'close', 'error']); // close acts as back to login here? No, requested "back btn" goes to login. 
// "회원가입 창에서 해당 표시(close btn)를 누르면 로그인 창이 나와야 해" -> close emits 'close', parent handles navigation.

const userStore = useUserStore();
const signUpId = ref('');
const signUpPw = ref('');
const signUpPwConfirm = ref('');
const signUpNickname = ref('');
const isIdChecked = ref(false);

const isNicknameChecked = ref(false);
const idCheckMsg = ref('');
const idCheckStatus = ref<'success' | 'error' | ''>('');
const nicknameCheckMsg = ref('');
const nicknameCheckStatus = ref<'success' | 'error' | ''>('');

const pwCheckMsg = ref('');
const pwCheckStatus = ref<'success' | 'error' | ''>('');

const checkPasswordMatch = () => {
    if (!signUpPwConfirm.value) {
        pwCheckMsg.value = "";
        pwCheckStatus.value = "";
        return;
    }
    if (signUpPw.value === signUpPwConfirm.value) {
        pwCheckMsg.value = "비밀번호가 일치합니다.";
        pwCheckStatus.value = "success";
    } else {
        pwCheckMsg.value = "비밀번호가 일치하지 않습니다.";
        pwCheckStatus.value = "error";
    }
};

const checkIdDuplicate = async () => {
    if (!signUpId.value.trim()) {
        idCheckMsg.value = "아이디를 입력해주세요.";
        idCheckStatus.value = 'error';
        return;
    }
    // TODO: API 연결
    if (signUpId.value.toLowerCase().includes('admin')) {
        idCheckMsg.value = "사용할 수 없는 아이디입니다.";
        idCheckStatus.value = 'error';
        isIdChecked.value = false;
    } else {
        idCheckMsg.value = "사용 가능한 아이디입니다.";
        idCheckStatus.value = 'success';
        isIdChecked.value = true;
    }
};

const checkNicknameDuplicate = async () => {
    if (!signUpNickname.value.trim()) {
        nicknameCheckMsg.value = "닉네임을 입력해주세요.";
        nicknameCheckStatus.value = 'error';
        return;
    }
    
    const { isAvailable, message } = await userStore.checkNickname(signUpNickname.value.trim());
    nicknameCheckMsg.value = message;
    nicknameCheckStatus.value = isAvailable ? 'success' : 'error';
    isNicknameChecked.value = isAvailable;
};

const confirmSignUp = async () => {
    if (!signUpId.value || !signUpPw.value || !signUpPwConfirm.value || !signUpNickname.value) {
        emit('error', "모든 정보를 입력해주세요!");
        return;
    }
    if (!isIdChecked.value) {
        emit('error', "아이디 중복 확인을 해주세요!");
        return;
    }
    if (!isNicknameChecked.value) {
        emit('error', "닉네임 중복 확인을 해주세요!");
        return;
    }
    if (signUpPw.value !== signUpPwConfirm.value) {
        emit('error', "비밀번호가 일치하지 않습니다!");
        return;
    }

    // 1. 회원가입 요청
    const signUpResult = await userStore.signUp(signUpId.value, signUpPw.value, signUpNickname.value);
    
    if (!signUpResult.success) {
        emit('error', getErrorMessage((signUpResult as any).error) || "회원가입 실패");
        return;
    }

    // 2. 가입 성공 시 자동 로그인
    const loginResult = await userStore.login(signUpId.value, signUpPw.value); 
    if (loginResult.success) {
        emit('success');
    } else {
        emit('error', "회원가입은 성공했으나 로그인에 실패했습니다.");
    }
};
</script>

<template>
    <div class="auth-form-container">
        <!-- Close Button (Back to Login) -->
        <button class="close-btn" @click="$emit('close')">
            <span class="material-symbols-outlined">close</span>
        </button>

        <div class="input-area">
            <div class="input-group mb-2">
                <div class="input-row">
                    <div class="input-wrapper flex-grow">
                        <input type="text" v-model="signUpId" placeholder="아이디" />
                    </div>
                    <button class="cartoon-btn secondary small-btn" @click="checkIdDuplicate">확인</button>
                </div>
                <p v-if="idCheckMsg" class="check-msg" :class="idCheckStatus">{{ idCheckMsg }}</p>
            </div>

            <div class="input-wrapper mb-2">
                <input type="password" v-model="signUpPw" @input="checkPasswordMatch" placeholder="비밀번호" />
            </div>
            
            <div class="input-group mb-2">
                <div class="input-wrapper">
                    <input type="password" v-model="signUpPwConfirm" @input="checkPasswordMatch" placeholder="비밀번호 확인" />
                </div>
                <p v-if="pwCheckMsg" class="check-msg" :class="pwCheckStatus">{{ pwCheckMsg }}</p>
            </div>

            <div class="input-group mb-4">
                <div class="input-row">
                    <div class="input-wrapper flex-grow">
                        <input type="text" v-model="signUpNickname" placeholder="닉네임" />
                    </div>
                    <button class="cartoon-btn secondary small-btn" @click="checkNicknameDuplicate">확인</button>
                </div>
                <p v-if="nicknameCheckMsg" class="check-msg" :class="nicknameCheckStatus">{{ nicknameCheckMsg }}</p>
            </div>

            <button @click="confirmSignUp" class="cartoon-btn primary large-btn">
                <span>회원가입</span>
                <span class="material-symbols-outlined">person_add</span>
            </button>
        </div>
    </div>
</template>

<style scoped>
/* 공통 스타일 복사 */
.mb-2 { margin-bottom: 0.5rem; }
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

.cartoon-btn.secondary { background: #ff7043; box-shadow: 0 5px 0 #d84315; }
.cartoon-btn.secondary:hover { background: #ff8a65; }
.cartoon-btn.secondary:active { top: 5px; box-shadow: 0 0 0 #d84315; }

.cartoon-btn.small-btn {
  width: auto;
  padding: 8px 12px;
  font-size: 1rem;
  white-space: nowrap;
}

.input-area { display: flex; flex-direction: column; gap: 12px; }
.input-group { display: flex; flex-direction: column; gap: 4px; }

.input-wrapper { position: relative; border: 3px solid #d7ccc8; border-radius: 12px; background: #fff; transition: all 0.3s; }
.input-wrapper:focus-within { border-color: var(--leaf-green); transform: scale(1.02); }
.input-area input { width: 100%; border: none; background: transparent; padding: 10px; font-size: 1.3rem; text-align: center; color: var(--text-brown); font-family: 'Jua', sans-serif; outline: none; }
.input-area input::placeholder { color: #bcaaa4; font-size: 1.1rem; }

.input-row { display: flex; gap: 8px; align-items: center; }
.flex-grow { flex-grow: 1; }

.check-msg { font-size: 1.2rem; margin-left: 4px; font-family: 'Nanum Pen Script', cursive; }
.check-msg.success { color: var(--leaf-dark); }
.check-msg.error { color: #d32f2f; }

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
