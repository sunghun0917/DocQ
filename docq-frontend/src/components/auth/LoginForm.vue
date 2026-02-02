<script setup lang="ts">
import { ref } from 'vue';
import { useUserStore } from '@/stores/userStore';
import { getErrorMessage } from '@/services/api';

const emit = defineEmits(['success', 'close', 'to-signup', 'error']);

const userStore = useUserStore();
const loginId = ref('');
const loginPw = ref('');

const confirmLogin = async () => {
    if (!loginId.value || !loginPw.value) {
        emit('error', "아이디와 비밀번호를 입력해주세요!");
        return;
    }
  
    const result = await userStore.login(loginId.value, loginPw.value);
    if (!result.success) {
        emit('error', getErrorMessage((result as any).error) || "로그인 실패");
        return;
    }
    emit('success');
};
</script>

<template>
    <div class="auth-form-container">
        <!-- Close Button -->
        <button class="close-btn" @click="$emit('close')">
            <span class="material-symbols-outlined">close</span>
        </button>

        <div class="input-area">
            <div class="input-wrapper mb-2">
                <input type="text" v-model="loginId" placeholder="아이디" autofocus />
            </div>
            <div class="input-wrapper mb-4">
                <input type="password" v-model="loginPw" placeholder="비밀번호" @keyup.enter="confirmLogin" />
            </div>
            <button @click="confirmLogin" class="cartoon-btn primary large-btn mb-2">
                <span>START</span>
                <span class="material-symbols-outlined">play_arrow</span>
            </button>
            <button @click="$emit('to-signup')" class="cartoon-btn secondary large-btn">
                <span>Sign up</span>
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

.input-area { display: flex; flex-direction: column; gap: 12px; }
.input-wrapper { position: relative; border: 3px solid #d7ccc8; border-radius: 12px; background: #fff; transition: all 0.3s; }
.input-wrapper:focus-within { border-color: var(--leaf-green); transform: scale(1.02); }
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
